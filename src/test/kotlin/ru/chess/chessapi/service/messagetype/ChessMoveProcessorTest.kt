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
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Models.Message.moveMessage
import ru.chess.chessapi.TestData.Rooms.CURRENT_INSTANT
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.TimeService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.service.sessions.RoomAcceptedMessagesStateService
import ru.chess.chessapi.web.websocket.message.MoveAcceptedMessage
import ru.chess.chessapi.web.websocket.message.enums.PromotionType

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

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `process - stores accepted move and sends it to both players`(promotion: Boolean) {
        val message = moveMessage(promotionType = if (promotion) PromotionType.QUEEN else null)
        val room = roomEntity(id = ROOM_ID, updatedAt = CURRENT_INSTANT)
        every {
            distributorService.updateRoomHistory(message.room, message.move, message.promotionType, message.sideOfMove)
        } returns room
        val expected = MoveAcceptedMessage(
            room = ROOM_ID, sideOfMove = message.sideOfMove, move = message.move,
            moveId = message.moveId, ply = message.ply, promotionType = message.promotionType,
            turnServerTimeMs = CURRENT_INSTANT.toEpochMilli()
        )

        processor.process(message, session)

        verify(exactly = 1) {
            distributorService.updateRoomHistory(message.room, message.move, message.promotionType, message.sideOfMove)
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
        confirmVerified(distributorService, roomAcceptedMessagesStateService, wsSender)
    }

    @Test
    fun `process - uses current time when room has no update time`() {
        val message = moveMessage()
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
        every { distributorService.updateRoomHistory(any(), any(), any(), any()) } throws failure

        shouldThrow<IllegalStateException> { processor.process(moveMessage(), session) } shouldBe failure

        verify { listOf(wsSender, roomAcceptedMessagesStateService, timeService) wasNot Called }
    }
}
