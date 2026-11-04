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
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Models.Message.matchFinishedMessage
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.service.sessions.RoomAcceptedMessagesStateService
import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.SideType

@ExtendWith(MockKExtension::class)
class MatchFinishProcessorTest {
    @InjectMockKs
    private lateinit var processor: MatchFinishProcessor
    @MockK(relaxUnitFun = true)
    private lateinit var distributorService: DistributorService
    @MockK(relaxUnitFun = true)
    private lateinit var wsSender: WsSender
    @MockK(relaxUnitFun = true)
    private lateinit var roomAcceptedMessagesStateService: RoomAcceptedMessagesStateService
    @MockK(relaxUnitFun = true)
    private lateinit var session: WebSocketSession

    @ParameterizedTest
    @CsvSource(
        "CHECKMATE, WHITE, WHITE, false", "CHECKMATE, BLACK, WHITE, true",
        "SURRENDER, WHITE, WHITE, true", "SURRENDER, BLACK, WHITE, false",
        "TIMEOUT, WHITE, WHITE, true", "TIMEOUT, BLACK, WHITE, false",
        "DRAW, WHITE, WHITE, true", "DRAW, BLACK, WHITE, false",
        "CHECKMATE, WHITE, BLACK, true", "CHECKMATE, BLACK, BLACK, false",
        "SURRENDER, WHITE, BLACK, false", "SURRENDER, BLACK, BLACK, true",
        "TIMEOUT, WHITE, BLACK, false", "TIMEOUT, BLACK, BLACK, true",
        "DRAW, WHITE, BLACK, false", "DRAW, BLACK, BLACK, true"
    )
    fun `process - notifies other player and clears accepted moves`(
        finishType: FinishType, winnerSide: SideType, user1Side: SideType, sendToUser1: Boolean
    ) {
        val message = matchFinishedMessage(winnerSide = winnerSide, finishType = finishType)
        val room = roomEntity(
            id = ROOM_ID, user1Side = user1Side,
            user2Side = if (user1Side == SideType.WHITE) SideType.BLACK else SideType.WHITE
        )
        every { distributorService.updateRoomHistoryWhenMatchIsOver(ROOM_ID, winnerSide, finishType) } returns room
        val recipient = if (sendToUser1) USER_ID_1 else USER_ID_2

        processor.process(message, session)

        verify(exactly = 1) {
            distributorService.updateRoomHistoryWhenMatchIsOver(ROOM_ID, winnerSide, finishType)
            wsSender.sendMessageIfSessionExist(recipient, message)
            roomAcceptedMessagesStateService.clearForBothRoomUsers(room)
        }
        verifyOrder {
            distributorService.updateRoomHistoryWhenMatchIsOver(ROOM_ID, winnerSide, finishType)
            wsSender.sendMessageIfSessionExist(recipient, message)
            roomAcceptedMessagesStateService.clearForBothRoomUsers(room)
        }
        confirmVerified(distributorService, wsSender, roomAcceptedMessagesStateService)
    }

    @Test
    fun `process - does not notify or clear moves when finish fails`() {
        val failure = IllegalStateException("Cannot finish match")
        every { distributorService.updateRoomHistoryWhenMatchIsOver(any(), any(), any()) } throws failure

        shouldThrow<IllegalStateException> { processor.process(matchFinishedMessage(), session) } shouldBe failure

        verify { listOf(wsSender, roomAcceptedMessagesStateService) wasNot Called }
    }
}
