package ru.chess.chessapi.service

import mu.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.entity.MoveEntity
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.repository.MoveRepository
import ru.chess.chessapi.repository.RoomRepository
import ru.chess.chessapi.web.websocket.message.*
import ru.chess.chessapi.web.websocket.message.enums.*
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Service
class OnlineMatchService(
    private val roomRepository: RoomRepository,
    private val moveRepository: MoveRepository,
    private val wsSender: WsSender,
    private val scheduler: TaskScheduler,
    private val clock: Clock,
    private val transactions: TransactionTemplate
) {
    private val logger = KotlinLogging.logger {}
    // shortcut: timers and WebSocket sessions belong to one process; add shared delivery before scaling replicas.
    private val statusTasks = ConcurrentHashMap<UUID, ScheduledFuture<*>>()
    // Keep snapshot delivery and session replacement ordered with moves, including afterCommit callbacks.
    private val roomLocks = Array(64) { Any() }

    @EventListener(ApplicationReadyEvent::class)
    fun restoreMatches() {
        roomRepository.findAllByGameTypeAndWinTypeIsNull(GameType.ONLINE)
            .filter { it.matchServerTimeMs != null }
            .forEach { updateMatchStatus(it.id!!) }
    }

    fun startMatch(roomId: UUID) = withRoom(roomId) { room ->
        if (room.winType != null) return@withRoom
        if (room.matchServerTimeMs == null) {
            room.matchServerTimeMs = clock.millis()
            roomRepository.save(room)
        }
        afterCommit { wsSender.sendRoomCreatedMessage(room) }
        scheduleAfterCommit(roomId, room.matchServerTimeMs!! + TURN_TIMEOUT_MS)
    }

    fun acceptMove(message: MoveMessageDto, userId: UUID) = withRoom(message.room) { room ->
        val playerSide = sideOf(room, userId) ?: return@withRoom
        if (message.backendUserId != null && message.backendUserId != userId) return@withRoom
        if (message.sideOfMove != playerSide || room.matchServerTimeMs == null) return@withRoom
        val now = clock.millis()
        if (!updateStatus(room, now)) return@withRoom

        val accepted = moveRepository.findByRoomIdAndMoveId(message.room, message.moveId)
        if (accepted != null) {
            if (accepted.sideOfMove == playerSide) broadcastAfterCommit(room, accepted.toMessage())
            return@withRoom
        }
        val lastMove = moveRepository.findFirstByRoomIdOrderByPlyDesc(message.room)
        val expectedPly = (lastMove?.ply ?: 0) + 1
        val expectedSide = if (expectedPly % 2 == 1) SideType.WHITE else SideType.BLACK
        if (message.ply != expectedPly || playerSide != expectedSide || !MOVE_PATTERN.matches(message.move)) {
            logger.warn { "Ignoring invalid move or ply in room ${message.room}" }
            return@withRoom
        }
        val turn = moveRepository.save(MoveEntity(
            roomId = message.room,
            moveId = message.moveId,
            ply = expectedPly,
            sideOfMove = playerSide,
            move = message.move,
            promotionType = message.promotionType,
            turnServerTimeMs = now
        ))
        val historyMove = message.moveForHistory?.takeIf { it.isNotBlank() }
            ?: (message.move + (message.promotionType?.toHistoryPart() ?: ""))
        room.history = listOfNotNull(room.history?.takeIf { it.isNotBlank() }, historyMove).joinToString(" ")
        roomRepository.save(room)
        broadcastAfterCommit(room, turn.toMessage())
        scheduleAfterCommit(message.room, now + TURN_TIMEOUT_MS)
    }

    fun updateMatchStatus(roomId: UUID) = withRoom(roomId) { room ->
        updateStatus(room, clock.millis())
    }

    fun finishMatch(message: MatchFinishedMessageDto, userId: UUID) = withRoom(message.room) { room ->
        if (sideOf(room, userId) == null) return@withRoom
        if (!updateStatus(room, clock.millis())) return@withRoom
        // Only the server clock can declare a timeout.
        if (message.finishType == FinishType.TIMEOUT) return@withRoom
        if (message.finishType != FinishType.DRAW && message.winnerSide !in listOf(SideType.WHITE, SideType.BLACK)) {
            return@withRoom
        }
        finish(room, if (message.finishType == FinishType.DRAW) null else message.winnerSide, message.finishType)
    }

    fun reconnect(roomId: UUID, userId: UUID, session: WebSocketSession) = withRoom(roomId) { room ->
        if (sideOf(room, userId) == null) return@withRoom
        val turns = moveRepository.findAllByRoomIdOrderByPly(roomId).map { it.toMessage() }
        val snapshot = NewWsSession(
            messageType = MessageType.NEW_WS,
            backendUserId = userId,
            sessionId = session.id,
            room = roomId,
            turns = turns,
            matchServerTimeMs = room.matchServerTimeMs
        )
        afterCommit {
            wsSender.saveSessionAndSendNewSessionMessage(userId, session, snapshot)
        }
        val wasFinished = room.winType != null
        updateStatus(room, clock.millis())
        if (wasFinished) afterCommit { wsSender.sendMessage(userId, finishMessage(room), session) }
    }

    private fun updateStatus(room: RoomEntity, now: Long): Boolean {
        if (room.winType != null) {
            afterCommit { cancelStatusTask(room.id!!) }
            return false
        }
        val start = room.matchServerTimeMs ?: return false
        val lastMove = moveRepository.findFirstByRoomIdOrderByPlyDesc(room.id!!)
        val deadline = (lastMove?.turnServerTimeMs ?: start) + TURN_TIMEOUT_MS
        if (now >= deadline) {
            finish(room, lastMove?.sideOfMove ?: SideType.BLACK, FinishType.TIMEOUT)
            return false
        }
        scheduleAfterCommit(room.id!!, deadline)
        return true
    }

    private fun finish(room: RoomEntity, winner: SideType?, type: FinishType) {
        room.winType = type
        room.winnerSide = winner
        roomRepository.save(room)
        afterCommit { cancelStatusTask(room.id!!) }
        broadcastAfterCommit(room, finishMessage(room))
    }

    private fun finishMessage(room: RoomEntity) = MatchFinishedMessageDto(
        messageType = MessageType.MATCH_FINISHED_WS,
        room = room.id!!,
        winnerSide = room.winnerSide,
        finishType = room.winType!!
    )

    private fun sideOf(room: RoomEntity, userId: UUID): SideType? = when (userId) {
        room.user1.id -> room.user1Side
        room.user2.id -> room.user2Side
        else -> null
    }

    private fun MoveEntity.toMessage() = MoveAcceptedMessageDto(
        room = roomId,
        sideOfMove = sideOfMove,
        move = move,
        moveId = moveId,
        ply = ply,
        promotionType = promotionType,
        turnServerTimeMs = turnServerTimeMs
    )

    private fun broadcastAfterCommit(room: RoomEntity, message: MessageDto) = afterCommit {
        wsSender.sendMessageIfSessionExist(room.user1.id!!, message)
        wsSender.sendMessageIfSessionExist(room.user2.id!!, message)
    }

    private fun scheduleAfterCommit(roomId: UUID, deadline: Long) = afterCommit {
        scheduleStatusTask(roomId, deadline)
    }

    private fun scheduleStatusTask(roomId: UUID, deadline: Long) {
        statusTasks.compute(roomId) { _, previous ->
            previous?.cancel(false)
            scheduler.schedule({
                try {
                    updateMatchStatus(roomId)
                } catch (ex: Exception) {
                    logger.error(ex) { "Cannot update match status for $roomId; retrying" }
                    scheduleStatusTask(roomId, clock.millis() + TURN_TIMEOUT_MS)
                }
            }, Instant.ofEpochMilli(deadline))
        }
    }

    private fun cancelStatusTask(roomId: UUID) {
        statusTasks.remove(roomId)?.cancel(false)
    }

    private fun withRoom(roomId: UUID, action: (RoomEntity) -> Unit) {
        synchronized(roomLocks[(roomId.hashCode() and Int.MAX_VALUE) % roomLocks.size]) {
            transactions.executeWithoutResult {
                val room = roomRepository.findByIdForUpdate(roomId) ?: return@executeWithoutResult
                if (room.gameType == GameType.ONLINE) action(room)
            }
        }
    }

    private fun afterCommit(action: () -> Unit) {
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = action()
        })
    }

    companion object {
        private const val TURN_TIMEOUT_MS = 60_000L
        private val MOVE_PATTERN = Regex("[A-H][1-8][A-H][1-8]")
    }
}
