package ru.chess.chessapi.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.scheduling.TaskScheduler
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.entity.MoveEntity
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.repository.MoveRepository
import ru.chess.chessapi.repository.RoomRepository
import ru.chess.chessapi.web.websocket.message.*
import ru.chess.chessapi.web.websocket.message.enums.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicLong

class OnlineMatchServiceTest {
    private val start = 1_791_800_000_123L
    private val now = AtomicLong(start)
    private val whiteId = UUID.randomUUID()
    private val blackId = UUID.randomUUID()
    private val roomId = UUID.randomUUID()
    private val room = RoomEntity(
        id = roomId,
        user1 = UserEntity(whiteId, "white", null, totalPoints = 0),
        user1Side = SideType.WHITE,
        user1Name = "white",
        user2 = UserEntity(blackId, "black", null, totalPoints = 0),
        user2Side = SideType.BLACK,
        user2Name = "black",
        gameType = GameType.ONLINE,
        history = null,
        winnerSide = null,
        winType = null,
        matchServerTimeMs = start
    )
    private val moves = CopyOnWriteArrayList<MoveEntity>()
    private val deliveries = CopyOnWriteArrayList<Delivery>()
    private val scheduled = CopyOnWriteArrayList<Scheduled>()
    private val createdRooms = CopyOnWriteArrayList<RoomEntity>()
    private val transactionManager = TestTransactionManager()
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(now.get())
    }
    private val roomRepository = Mockito.mock(RoomRepository::class.java) { invocation ->
        when (invocation.method.name) {
            "findByIdForUpdate" -> room.takeIf { invocation.arguments[0] == roomId }
            "findAllByGameTypeAndWinTypeIsNull" -> listOfNotNull(room.takeIf { it.winType == null })
            "save" -> invocation.arguments[0]
            else -> Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val moveRepository = Mockito.mock(MoveRepository::class.java) { invocation ->
        when (invocation.method.name) {
            "findAllByRoomIdOrderByPly" -> moves.sortedBy { it.ply }
            "findByRoomIdAndMoveId" -> moves.firstOrNull { it.moveId == invocation.arguments[1] }
            "findFirstByRoomIdOrderByPlyDesc" -> moves.maxByOrNull { it.ply }
            "countByRoomId" -> moves.size.toLong()
            "save" -> (invocation.arguments[0] as MoveEntity).also { moves.add(it) }
            else -> Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val wsSender = Mockito.mock(WsSender::class.java) { invocation ->
        when (invocation.method.name) {
            "sendRoomCreatedMessage" -> {
                assertTrue(transactionManager.committed, "ROOM_FOUND must follow the transaction commit")
                createdRooms.add(invocation.arguments[0] as RoomEntity)
                null
            }
            "sendMessage", "sendMessageIfSessionExist" -> {
                assertTrue(transactionManager.committed, "WebSocket messages must follow the transaction commit")
                deliveries.add(Delivery(
                    invocation.arguments[0] as UUID,
                    invocation.arguments[1] as MessageDto,
                    invocation.arguments.getOrNull(2) as WebSocketSession?
                ))
                null
            }
            "saveSessionAndSendNewSessionMessage" -> {
                assertTrue(transactionManager.committed, "NEW_WS must follow the transaction commit")
                deliveries.add(Delivery(
                    invocation.arguments[0] as UUID,
                    invocation.arguments[2] as NewWsSession,
                    invocation.arguments[1] as WebSocketSession
                ))
                null
            }
            else -> Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val scheduler = Mockito.mock(TaskScheduler::class.java) { invocation ->
        if (invocation.method.name == "schedule") {
            val future = Mockito.mock(ScheduledFuture::class.java)
            scheduled.add(Scheduled(invocation.arguments[0] as Runnable, invocation.arguments[1] as Instant, future))
            future
        } else {
            Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val service = newService()

    @Test
    fun `starting a match persists millisecond timestamp and schedules first move timeout`() {
        room.matchServerTimeMs = null

        service.startMatch(roomId)

        assertEquals(start, room.matchServerTimeMs)
        assertEquals(listOf(room), createdRooms)
        assertTrue(moves.isEmpty())
        assertEquals(Instant.ofEpochMilli(start + 60_000), scheduled.last().at)
    }

    @Test
    fun `accepted move is saved with server time and promotion and acknowledged to both players`() {
        now.set(start + 1_234)
        val move = move().copy(move = "A7A8", promotionType = PromotionType.QUEEN, moveForHistory = "a8=Q")

        service.acceptMove(move, whiteId)

        val saved = moves.single()
        assertEquals(move.moveId, saved.moveId)
        assertEquals(roomId, saved.roomId)
        assertEquals(1, saved.ply)
        assertEquals("A7A8", saved.move)
        assertEquals(PromotionType.QUEEN, saved.promotionType)
        assertEquals(now.get(), saved.turnServerTimeMs)
        assertEquals("a8=Q", room.history)
        assertEquals(setOf(whiteId, blackId), deliveries.map { it.userId }.toSet())
        val ack = acknowledgements().first()
        assertEquals(MessageType.CHESS_MOVE_ACCEPTED, ack.messageType)
        assertEquals(move.moveId, ack.moveId)
        assertEquals(saved.turnServerTimeMs, ack.turnServerTimeMs)
        assertEquals(saved.promotionType, ack.promotionType)
        assertEquals(listOf(ack, ack), acknowledgements())
        assertEquals(Instant.ofEpochMilli(now.get() + 60_000), scheduled.last().at)
        Mockito.verify(roomRepository).findByIdForUpdate(roomId)
    }

    @Test
    fun `retry of moveId sends original ack without appending or extending deadline`() {
        val move = move()
        service.acceptMove(move, whiteId)
        val original = acknowledgements().first()
        now.set(start + 20_000)

        service.acceptMove(move.copy(move = "A1A4", ply = 99), whiteId)

        assertEquals(1, moves.size)
        assertEquals("E2E4", room.history)
        assertEquals(listOf(original, original, original, original), acknowledgements())
        assertEquals(Instant.ofEpochMilli(start + 60_000), scheduled.last().at)
    }

    @Test
    fun `gap in ply or wrong first side cannot be saved`() {
        service.acceptMove(move(ply = 2), whiteId)
        service.acceptMove(move(ply = 1, side = SideType.BLACK), blackId)
        service.acceptMove(move(ply = 0), whiteId)

        assertTrue(moves.isEmpty())
        assertTrue(deliveries.isEmpty())
        assertNull(room.history)
    }

    @Test
    fun `authenticated sender cannot submit opponent move or enter another room`() {
        service.acceptMove(move(), blackId)
        service.acceptMove(move(), UUID.randomUUID())
        service.acceptMove(move().copy(backendUserId = blackId), whiteId)

        assertTrue(moves.isEmpty())
        assertTrue(deliveries.isEmpty())
    }

    @Test
    fun `malformed coordinates cannot be persisted`() {
        for (coordinates in listOf("E2E9", "A0A1", "E2E4Q", "DROP TABLE")) {
            service.acceptMove(move().copy(move = coordinates), whiteId)
        }

        assertTrue(moves.isEmpty())
        assertTrue(deliveries.isEmpty())
    }

    @Test
    fun `next move must alternate sides and use exactly the next ply`() {
        service.acceptMove(move(), whiteId)
        service.acceptMove(move(ply = 2), whiteId)
        service.acceptMove(move(ply = 3, side = SideType.BLACK), blackId)
        service.acceptMove(move(ply = 2, side = SideType.BLACK).copy(move = "E7E5"), blackId)

        assertEquals(listOf(1, 2), moves.map { it.ply })
        assertEquals("E2E4 E7E5", room.history)
        assertEquals(4, acknowledgements().size)
    }

    @Test
    fun `failed commit cannot acknowledge a move or schedule its timer`() {
        transactionManager.failCommit = true

        assertThrows(IllegalStateException::class.java) { service.acceptMove(move(), whiteId) }

        assertTrue(deliveries.isEmpty())
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun `promotion is preserved in fallback history on the first move`() {
        service.acceptMove(move().copy(move = "A7A8", promotionType = PromotionType.KNIGHT), whiteId)

        assertEquals("A7A8N", room.history)
        assertEquals(PromotionType.KNIGHT, moves.single().promotionType)
    }

    @Test
    fun `opponent cannot claim a stored moveId as their own`() {
        val message = move()
        service.acceptMove(message, whiteId)
        deliveries.clear()

        service.acceptMove(message.copy(sideOfMove = SideType.BLACK, ply = 2), blackId)

        assertEquals(1, moves.size)
        assertTrue(deliveries.isEmpty())
    }

    @Test
    fun `timer before first move expires exactly at sixty seconds and broadcasts once`() {
        now.set(start + 59_999)
        service.updateMatchStatus(roomId)
        assertNull(room.winType)
        assertEquals(Instant.ofEpochMilli(start + 60_000), scheduled.last().at)

        now.incrementAndGet()
        scheduled.last().task.run()
        service.updateMatchStatus(roomId)

        assertEquals(FinishType.TIMEOUT, room.winType)
        assertEquals(SideType.BLACK, room.winnerSide)
        assertEquals(2, finishes().size)
        assertTrue(finishes().all { it.messageType == MessageType.MATCH_FINISHED_WS && it.winnerSide == SideType.BLACK })
        assertEquals(setOf(whiteId, blackId), deliveries.map { it.userId }.toSet())
    }

    @Test
    fun `move resets timeout to sixty seconds after its server timestamp`() {
        service.startMatch(roomId)
        val firstTimer = scheduled.last()
        now.set(start + 45_000)
        service.acceptMove(move(), whiteId)

        Mockito.verify(firstTimer.future, Mockito.atLeastOnce()).cancel(false)
        assertEquals(Instant.ofEpochMilli(start + 105_000), scheduled.last().at)
        now.set(start + 60_000)
        firstTimer.task.run()
        assertNull(room.winType, "A stale timer cannot finish a match before the new deadline")
        now.set(start + 104_999)
        service.updateMatchStatus(roomId)
        assertNull(room.winType)
        now.incrementAndGet()
        service.updateMatchStatus(roomId)

        assertEquals(FinishType.TIMEOUT, room.winType)
        assertEquals(SideType.WHITE, room.winnerSide)
        assertEquals(2, finishes().size)
    }

    @Test
    fun `move at expired deadline is rejected before it can reset the timer`() {
        now.set(start + 60_000)

        service.acceptMove(move(), whiteId)

        assertTrue(moves.isEmpty())
        assertTrue(acknowledgements().isEmpty())
        assertEquals(FinishType.TIMEOUT, room.winType)
        assertEquals(SideType.BLACK, room.winnerSide)
        assertEquals(2, finishes().size)
    }

    @Test
    fun `reconnect after service restart returns persisted ordered moves only to requester`() {
        service.acceptMove(move(), whiteId)
        now.addAndGet(1_000)
        service.acceptMove(move(ply = 2, side = SideType.BLACK).copy(move = "E7E5"), blackId)
        deliveries.clear()
        val session = session()

        newService().reconnect(roomId, whiteId, session)

        val delivery = deliveries.single()
        assertEquals(whiteId, delivery.userId)
        assertSame(session, delivery.session)
        val snapshot = delivery.message as NewWsSession
        assertEquals(MessageType.NEW_WS, snapshot.messageType)
        assertEquals(roomId, snapshot.room)
        assertEquals(whiteId, snapshot.backendUserId)
        assertEquals(session.id, snapshot.sessionId)
        assertEquals(start, snapshot.matchServerTimeMs)
        assertEquals(listOf(1, 2), snapshot.turns.map { it.ply })
        assertEquals(moves.map { it.moveId }, snapshot.turns.map { it.moveId })
        assertEquals(listOf(start, start + 1_000), snapshot.turns.map { it.turnServerTimeMs })
    }

    @Test
    fun `non participant cannot obtain reconnect snapshot`() {
        service.reconnect(roomId, UUID.randomUUID(), session())

        assertTrue(deliveries.isEmpty())
    }

    @Test
    fun `reconnect before first move includes empty turns and match start in milliseconds`() {
        service.reconnect(roomId, blackId, session())

        val delivery = deliveries.single()
        val snapshot = delivery.message as NewWsSession
        assertEquals(blackId, delivery.userId)
        assertTrue(snapshot.turns.isEmpty())
        assertEquals(start, snapshot.matchServerTimeMs)
        assertEquals(roomId, snapshot.room)
    }

    @Test
    fun `finished match stays terminal and reconnect sends snapshot followed by result`() {
        service.startMatch(roomId)
        val timer = scheduled.last()
        service.finishMatch(finish(FinishType.SURRENDER, SideType.BLACK), whiteId)
        Mockito.verify(timer.future, Mockito.atLeastOnce()).cancel(false)
        assertEquals(FinishType.SURRENDER, room.winType)
        assertEquals(SideType.BLACK, room.winnerSide)
        assertEquals(2, finishes().size)

        service.finishMatch(finish(FinishType.CHECKMATE, SideType.WHITE), blackId)
        service.acceptMove(move(), whiteId)
        now.set(start + 120_000)
        service.updateMatchStatus(roomId)
        assertEquals(FinishType.SURRENDER, room.winType)
        assertTrue(moves.isEmpty())
        assertEquals(2, finishes().size)
        deliveries.clear()
        val session = session()

        service.reconnect(roomId, whiteId, session)

        assertEquals(listOf(MessageType.NEW_WS, MessageType.MATCH_FINISHED_WS), deliveries.map { it.message.messageType })
        assertTrue(deliveries.all { it.userId == whiteId && it.session === session })
        assertEquals(FinishType.SURRENDER, finishes().single().finishType)
        assertEquals(SideType.BLACK, finishes().single().winnerSide)
    }

    @Test
    fun `client cannot declare timeout early or finish another players room`() {
        service.finishMatch(finish(FinishType.TIMEOUT, SideType.WHITE), whiteId)
        service.finishMatch(finish(FinishType.CHECKMATE, SideType.WHITE), UUID.randomUUID())

        assertNull(room.winType)
        assertTrue(deliveries.isEmpty())
    }

    @Test
    fun `restore schedules surviving match using its original last move deadline`() {
        now.addAndGet(10_000)
        service.acceptMove(move(), whiteId)
        now.addAndGet(20_000)
        deliveries.clear()

        newService().restoreMatches()

        assertEquals(Instant.ofEpochMilli(start + 70_000), scheduled.last().at)
        assertNull(room.winType)
        assertTrue(deliveries.isEmpty())
    }

    @Test
    fun `restore finishes a match whose deadline passed while server was down`() {
        now.set(start + 60_000)

        newService().restoreMatches()

        assertEquals(FinishType.TIMEOUT, room.winType)
        assertEquals(SideType.BLACK, room.winnerSide)
        assertEquals(2, finishes().size)
        assertTrue(scheduled.isEmpty())
    }

    private fun newService() = OnlineMatchService(
        roomRepository, moveRepository, wsSender, scheduler, clock, TransactionTemplate(transactionManager)
    )

    private fun move(ply: Int = 1, side: SideType = SideType.WHITE) = MoveMessageDto(
        messageType = MessageType.CHESS_MOVE,
        room = roomId,
        sideOfMove = side,
        move = "E2E4",
        moveId = UUID.randomUUID(),
        ply = ply
    )

    private fun finish(type: FinishType, winner: SideType?) = MatchFinishedMessageDto(
        MessageType.MATCH_FINISHED, roomId, winner, type
    )

    private fun session(): WebSocketSession = Mockito.mock(WebSocketSession::class.java).also {
        Mockito.`when`(it.id).thenReturn("reconnected-session")
    }

    private fun acknowledgements() = deliveries.mapNotNull { it.message as? MoveAcceptedMessageDto }
    private fun finishes() = deliveries.mapNotNull { it.message as? MatchFinishedMessageDto }
    private data class Delivery(val userId: UUID, val message: MessageDto, val session: WebSocketSession?)
    private data class Scheduled(val task: Runnable, val at: Instant, val future: ScheduledFuture<*>)

    private class TestTransactionManager : AbstractPlatformTransactionManager() {
        var committed = false
            private set
        var failCommit = false

        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) { committed = false }
        override fun doCommit(status: DefaultTransactionStatus) {
            check(!failCommit) { "Commit failed" }
            committed = true
        }
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }
}
