package ru.chess.chessapi.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.web.dto.request.RoomHistorySaveRequest
import ru.chess.chessapi.web.websocket.message.MatchFinishedMessageDto
import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.GameType
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.util.UUID

class RoomHistorySaveServiceTest {
    private val white = UserEntity(UUID.randomUUID(), "white", null, totalPoints = 0)
    private val black = UserEntity(UUID.randomUUID(), "black", null, totalPoints = 0)
    private val room = RoomEntity(
        id = UUID.randomUUID(),
        user1 = white, user1Side = SideType.WHITE, user1Name = white.username,
        user2 = black, user2Side = SideType.BLACK, user2Name = black.username,
        gameType = GameType.ONLINE, history = null, winnerSide = null, winType = null
    )
    private val request = RoomHistorySaveRequest(
        backendUserId = white.id, signature = null, room = room.id, userSide = SideType.WHITE,
        username = white.username, opponentName = black.username, opponentType = GameType.ONLINE,
        history = "1. e4 e5", winnerSide = "BLACK", finishType = FinishType.SURRENDER
    )
    private val roomService = Mockito.mock(RoomService::class.java).also {
        Mockito.`when`(it.findRoomByIdForUpdate(room.id!!)).thenReturn(room)
        Mockito.`when`(it.save(room)).thenReturn(room)
    }
    private val gameHistoryService = Mockito.mock(GameHistoryService::class.java)
    private val transactionManager = TestTransactionManager()
    private val transactions = TransactionTemplate(transactionManager)
    private val deliveries = mutableListOf<Pair<UUID, MatchFinishedMessageDto>>()
    private val wsSender = Mockito.mock(WsSender::class.java) { invocation ->
        if (invocation.method.name == "sendMessageIfSessionExist") {
            assertTrue(transactionManager.committed, "The final result must not be sent before commit")
            deliveries.add(invocation.arguments[0] as UUID to invocation.arguments[1] as MatchFinishedMessageDto)
            null
        } else {
            Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val service = RoomHistorySaveService(
        roomService, Mockito.mock(UserService::class.java), gameHistoryService, wsSender,
        ru.chess.chessapi.auth.PlayerAccessGuard()
    )

    @Test
    fun `HTTP finalization locks room and broadcasts canonical result only after commit`() {
        val response = transactions.execute {
            service.saveHistory(request).also { assertTrue(deliveries.isEmpty()) }
        }

        Mockito.verify(roomService).findRoomByIdForUpdate(room.id!!)
        Mockito.verify(roomService, Mockito.never()).findRoomById(room.id!!)
        Mockito.verify(gameHistoryService).saveGameHistoryByRoom(white, room, GameType.ONLINE)
        assertEquals(room.id, response!!.roomId)
        assertEquals(white.id, response.backendUserId)
        assertEquals(request.history, room.history)
        assertEquals(FinishType.SURRENDER, room.winType)
        assertEquals(SideType.BLACK, room.winnerSide)
        val expected = MatchFinishedMessageDto(
            MessageType.MATCH_FINISHED_WS, room.id!!, SideType.BLACK, FinishType.SURRENDER
        )
        assertEquals(listOf(white.id!! to expected, black.id!! to expected), deliveries)
    }

    @Test
    fun `existing draw with null winner is not overwritten or announced again`() {
        room.winType = FinishType.DRAW
        room.winnerSide = null

        transactions.executeWithoutResult { service.saveHistory(request) }

        Mockito.verify(roomService).findRoomByIdForUpdate(room.id!!)
        assertEquals(FinishType.DRAW, room.winType)
        assertNull(room.winnerSide)
        assertTrue(deliveries.isEmpty())
    }

    @Test
    fun `repeated HTTP save does not broadcast another final result`() {
        transactions.executeWithoutResult { service.saveHistory(request) }
        val original = deliveries.toList()

        transactions.executeWithoutResult {
            service.saveHistory(request.copy(winnerSide = "WHITE", finishType = FinishType.CHECKMATE))
        }

        assertEquals(original, deliveries)
        assertEquals(FinishType.SURRENDER, room.winType)
        assertEquals(SideType.BLACK, room.winnerSide)
    }

    @Test
    fun `rolled back history save never broadcasts completion`() {
        transactions.executeWithoutResult {
            service.saveHistory(request)
            it.setRollbackOnly()
        }

        assertTrue(deliveries.isEmpty())
    }

    private class TestTransactionManager : AbstractPlatformTransactionManager() {
        var committed = false
            private set

        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) { committed = false }
        override fun doCommit(status: DefaultTransactionStatus) { committed = true }
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }
}
