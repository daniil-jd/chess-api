package ru.chess.chessapi.service.messagetype

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Called
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Models.Message.moveMessage
import ru.chess.chessapi.TestData.Models.Message.moveAcceptedMessage
import ru.chess.chessapi.TestData.Rooms.CURRENT_INSTANT
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Sessions.SESSION_ID
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2
import ru.chess.chessapi.TestData.Users.USER_ID_3
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.TimeService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.service.sessions.RoomAcceptedMessagesStateService
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.web.websocket.message.MoveAcceptedMessage
import ru.chess.chessapi.web.websocket.message.enums.PromotionType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.util.UUID

@ExtendWith(MockKExtension::class)
class ChessMoveProcessorTest {
    @InjectMockKs
    private lateinit var processor: ChessMoveProcessor
    @MockK(relaxUnitFun = true)
    private lateinit var distributorService: DistributorService
    @MockK(relaxUnitFun = true)
    private lateinit var wsSender: WsSender
    @MockK(relaxUnitFun = true)
    private lateinit var roomAcceptedMessagesStateService: RoomAcceptedMessagesStateService
    @MockK(relaxUnitFun = true)
    private lateinit var timeService: TimeService
    @MockK(relaxUnitFun = true)
    private lateinit var session: WebSocketSession
    @MockK
    private lateinit var userSessionStateService: UserSessionStateService

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false", "true, true")
    fun `process - saves history notation and acknowledges original coordinates`(
        promotion: Boolean, hasHistoryNotation: Boolean
    ) {
        val move = moveMessage(promotionType = if (promotion) PromotionType.QUEEN else null)
        val message = move.copy(moveForHistory = move.moveForHistory.takeIf { hasHistoryNotation })
        val historyMove = message.moveForHistory ?: message.move
        val room = roomEntity(id = ROOM_ID, updatedAt = CURRENT_INSTANT)
        givenNewMove()
        every {
            distributorService.updateRoomHistory(message.room, historyMove, message.promotionType, message.sideOfMove)
        } returns room
        val expected = MoveAcceptedMessage(
            room = ROOM_ID, sideOfMove = message.sideOfMove, move = message.move,
            moveId = message.moveId, ply = message.ply, promotionType = message.promotionType,
            turnServerTimeMs = CURRENT_INSTANT.toEpochMilli()
        )

        processor.process(message, session)

        verify(exactly = 1) {
            distributorService.updateRoomHistory(message.room, historyMove, message.promotionType, message.sideOfMove)
            roomAcceptedMessagesStateService.addMessage(ROOM_ID, expected)
            wsSender.sendMessageIfSessionExist(USER_ID_1, expected)
            wsSender.sendMessageIfSessionExist(USER_ID_2, expected)
        }
        verifyOrder {
            roomAcceptedMessagesStateService.addMessage(ROOM_ID, expected)
            wsSender.sendMessageIfSessionExist(USER_ID_1, expected)
            wsSender.sendMessageIfSessionExist(USER_ID_2, expected)
        }
        verify { timeService wasNot Called }
        confirmVerified(wsSender)
    }

    @Test
    fun `process - uses current time when room has no update time`() {
        val message = moveMessage()
        givenNewMove()
        every { distributorService.updateRoomHistory(any(), any(), any(), any()) } returns roomEntity(id = ROOM_ID, updatedAt = null)
        every { timeService.getNowInstant() } returns CURRENT_INSTANT
        val expected = MoveAcceptedMessage(
            room = ROOM_ID, sideOfMove = message.sideOfMove, move = message.move,
            moveId = message.moveId, ply = message.ply, promotionType = null,
            turnServerTimeMs = CURRENT_INSTANT.toEpochMilli()
        )

        processor.process(message, session)

        verify(exactly = 1) {
            timeService.getNowInstant()
            roomAcceptedMessagesStateService.addMessage(ROOM_ID, expected)
            wsSender.sendMessageIfSessionExist(USER_ID_1, expected)
            wsSender.sendMessageIfSessionExist(USER_ID_2, expected)
        }
    }

    @Test
    fun `process - does not acknowledge move when history update fails`() {
        val failure = IllegalStateException("Cannot update history")
        givenNewMove()
        every { distributorService.updateRoomHistory(any(), any(), any(), any()) } throws failure

        shouldThrow<IllegalStateException> { processor.process(moveMessage(), session) } shouldBe failure

        verify { listOf(wsSender, timeService) wasNot Called }
        verify(exactly = 0) { roomAcceptedMessagesStateService.addMessage(any(), any()) }
    }

    @Test
    fun `process - resends the original acknowledgement for a repeated move`() {
        val original = moveAcceptedMessage()
        givenPlayer()
        every { roomAcceptedMessagesStateService.getMessageByRoomIdAndMoveId(ROOM_ID, original.moveId) } returns original

        processor.process(moveMessage().copy(ply = original.ply + 2), session)

        verify(exactly = 1) {
            wsSender.sendMessageIfSessionExist(USER_ID_1, original)
            wsSender.sendMessageIfSessionExist(USER_ID_2, original)
        }
        verify(exactly = 0) {
            distributorService.updateRoomHistory(any(), any(), any(), any())
            roomAcceptedMessagesStateService.addMessage(any(), any())
        }
    }

    @ParameterizedTest
    @CsvSource(
        "1, WHITE, false, true", "2, WHITE, false, false", "0, WHITE, false, false",
        "1, BLACK, false, false", "1, WHITE, true, false", "2, BLACK, true, true"
    )
    fun `process - checks first ply and player to move`(
        ply: Int, side: SideType, hasPreviousMove: Boolean, accepted: Boolean
    ) {
        val userId = if (side == SideType.WHITE) USER_ID_1 else USER_ID_2
        val message = moveMessage(sideOfMove = side).copy(backendUserId = userId, ply = ply)
        val historyMove = message.moveForHistory ?: message.move
        givenPlayer(userId)
        every { roomAcceptedMessagesStateService.getMessageByRoomIdAndMoveId(ROOM_ID, message.moveId) } returns null
        every { roomAcceptedMessagesStateService.getLastMoveByRoom(ROOM_ID) } returns
            if (hasPreviousMove) 1 else null
        if (accepted) {
            every { distributorService.updateRoomHistory(ROOM_ID, historyMove, null, side) } returns
                roomEntity(id = ROOM_ID, updatedAt = CURRENT_INSTANT)
        }

        processor.process(message, session)

        verify(exactly = if (accepted) 1 else 0) {
            distributorService.updateRoomHistory(ROOM_ID, historyMove, null, side)
            roomAcceptedMessagesStateService.addMessage(ROOM_ID, any())
        }
        if (!accepted) verify { wsSender wasNot Called }
    }

    @ParameterizedTest
    @ValueSource(strings = ["unknown room", "unknown socket", "mismatched user", "outsider", "wrong color"])
    fun `process - ignores a move from an invalid sender`(reason: String) {
        val userId = if (reason == "outsider") USER_ID_3 else USER_ID_1
        val message = moveMessage().copy(
            backendUserId = if (reason == "mismatched user") USER_ID_2 else userId,
            sideOfMove = if (reason == "wrong color") SideType.BLACK else SideType.WHITE
        )
        every { distributorService.findRoomById(ROOM_ID) } returns
            if (reason == "unknown room") null else roomEntity(id = ROOM_ID)
        every { session.id } returns SESSION_ID
        every { userSessionStateService.getUserIdBySession(SESSION_ID) } returns
            if (reason == "unknown socket") null else userId

        processor.process(message, session)

        verify(exactly = 0) { distributorService.updateRoomHistory(any(), any(), any(), any()) }
        verify { listOf(roomAcceptedMessagesStateService, wsSender, timeService) wasNot Called }
    }

    private fun givenPlayer(userId: UUID = USER_ID_1) {
        every { distributorService.findRoomById(ROOM_ID) } returns roomEntity(id = ROOM_ID)
        every { session.id } returns SESSION_ID
        every { userSessionStateService.getUserIdBySession(SESSION_ID) } returns userId
    }

    private fun givenNewMove() {
        givenPlayer()
        every { roomAcceptedMessagesStateService.getMessageByRoomIdAndMoveId(ROOM_ID, any()) } returns null
        every { roomAcceptedMessagesStateService.getLastMoveByRoom(ROOM_ID) } returns moveMessage().ply - 1
    }
}
