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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Models.Message.matchFinishedMessage
import ru.chess.chessapi.TestData.Sessions.SESSION_ID
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.service.sessions.RoomAcceptedMessagesStateService
import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.util.UUID

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
    @MockK
    private lateinit var userSessionStateService: UserSessionStateService

    @BeforeEach
    fun setup() {
        every { session.id } returns SESSION_ID
        every { userSessionStateService.getUserIdBySession(SESSION_ID) } returns USER_ID_1
        every { distributorService.findRoomById(ROOM_ID) } returns roomEntity(id = ROOM_ID)
    }

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
        every { distributorService.findRoomById(ROOM_ID) } returns room
        every { distributorService.updateRoomHistoryWhenMatchIsOver(ROOM_ID, winnerSide, finishType) } returns room
        val recipient = if (sendToUser1) USER_ID_1 else USER_ID_2

        processor.process(message, session)

        verify(exactly = 1) {
            distributorService.findRoomById(ROOM_ID)
            userSessionStateService.getUserIdBySession(SESSION_ID)
            distributorService.updateRoomHistoryWhenMatchIsOver(ROOM_ID, winnerSide, finishType)
            wsSender.sendMessageIfSessionExist(recipient, message)
            roomAcceptedMessagesStateService.clearByRoomId(ROOM_ID)
        }
        verifyOrder {
            distributorService.findRoomById(ROOM_ID)
            distributorService.updateRoomHistoryWhenMatchIsOver(ROOM_ID, winnerSide, finishType)
            wsSender.sendMessageIfSessionExist(recipient, message)
            roomAcceptedMessagesStateService.clearByRoomId(ROOM_ID)
        }
        confirmVerified(distributorService, wsSender, roomAcceptedMessagesStateService, userSessionStateService)
    }

    @ParameterizedTest
    @CsvSource("true, WHITE", "false, WHITE", "true, BLACK", "false, BLACK")
    fun `draw without winner notifies only the opponent of the actual sender`(firstSender: Boolean, user1Side: SideType) {
        val room = roomEntity(id = ROOM_ID, user1Side = user1Side,
            user2Side = if (user1Side == SideType.WHITE) SideType.BLACK else SideType.WHITE)
        val message = matchFinishedMessage(null, FinishType.DRAW)
        val sender = if (firstSender) USER_ID_1 else USER_ID_2
        val opponent = if (firstSender) USER_ID_2 else USER_ID_1
        every { userSessionStateService.getUserIdBySession(SESSION_ID) } returns sender
        every { distributorService.findRoomById(ROOM_ID) } returns room
        every { distributorService.updateRoomHistoryWhenMatchIsOver(ROOM_ID, null, FinishType.DRAW) } returns room

        processor.process(message, session)

        verifyOrder {
            userSessionStateService.getUserIdBySession(SESSION_ID)
            distributorService.findRoomById(ROOM_ID)
            distributorService.updateRoomHistoryWhenMatchIsOver(ROOM_ID, null, FinishType.DRAW)
            wsSender.sendMessageIfSessionExist(opponent, message)
            roomAcceptedMessagesStateService.clearByRoomId(ROOM_ID)
        }
        verify(exactly = 1) { wsSender.sendMessageIfSessionExist(any(), any()) }
        confirmVerified(wsSender, distributorService, roomAcceptedMessagesStateService, userSessionStateService)
    }

    @ParameterizedTest
    @EnumSource(value = FinishType::class, names = ["CHECKMATE", "SURRENDER", "TIMEOUT"])
    fun `null winner is rejected for decisive results`(finish: FinishType) {
        processor.process(matchFinishedMessage(null, finish), session)

        verify { listOf(distributorService, wsSender, roomAcceptedMessagesStateService, userSessionStateService) wasNot Called }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `unregistered sender or outsider cannot finish a room`(registered: Boolean) {
        every { userSessionStateService.getUserIdBySession(SESSION_ID) } returns
            if (registered) UUID.randomUUID() else null

        processor.process(matchFinishedMessage(null, FinishType.DRAW), session)

        verify(exactly = 0) { distributorService.updateRoomHistoryWhenMatchIsOver(any(), any(), any()) }
        verify { listOf(wsSender, roomAcceptedMessagesStateService) wasNot Called }
    }

    @Test
    fun `missing room does not finish or notify`() {
        every { distributorService.findRoomById(ROOM_ID) } returns null

        processor.process(matchFinishedMessage(), session)

        verify(exactly = 0) { distributorService.updateRoomHistoryWhenMatchIsOver(any(), any(), any()) }
        verify { listOf(wsSender, roomAcceptedMessagesStateService) wasNot Called }
    }

    @Test
    fun `process - does not notify or clear moves when finish fails`() {
        val failure = IllegalStateException("Cannot finish match")
        every { distributorService.updateRoomHistoryWhenMatchIsOver(any(), any(), any()) } throws failure

        shouldThrow<IllegalStateException> { processor.process(matchFinishedMessage(), session) } shouldBe failure

        verify { listOf(wsSender, roomAcceptedMessagesStateService) wasNot Called }
    }
}
