package ru.chess.chessapi.service.messagetype

import io.mockk.Called
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.TestData.Sessions.SESSION_ID
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.web.websocket.message.RequestForRoomCancelDto
import ru.chess.chessapi.web.websocket.message.enums.MessageType

@ExtendWith(MockKExtension::class)
class CancelMatchProcessorTest {

    @InjectMockKs
    private lateinit var cancelMatchProcessor: CancelMatchProcessor

    @MockK(relaxUnitFun = true)
    private lateinit var distributorService: DistributorService

    @MockK(relaxUnitFun = true)
    private lateinit var userSessionStateService: UserSessionStateService

    @MockK(relaxUnitFun = true)
    private lateinit var session: WebSocketSession

    @BeforeEach
    fun beforeEach() {
        every { session.id } returns SESSION_ID
    }

    @Test
    fun `process - cancels candidate for user found by session`() {
        val message = RequestForRoomCancelDto(MessageType.REQUEST_FOR_ROOM_CANCEL)
        every { userSessionStateService.getUserIdBySession(SESSION_ID) } returns USER_ID_1

        cancelMatchProcessor.process(message, session)

        verify(exactly = 1) { userSessionStateService.getUserIdBySession(SESSION_ID) }
        verify(exactly = 1) { distributorService.cancelRoomCandidate(USER_ID_1) }
        confirmVerified(userSessionStateService, distributorService)
    }

    @Test
    fun `process - does not cancel candidate when session has no user`() {
        val message = RequestForRoomCancelDto(MessageType.REQUEST_FOR_ROOM_CANCEL)
        every { userSessionStateService.getUserIdBySession(SESSION_ID) } returns null

        cancelMatchProcessor.process(message, session)

        verify(exactly = 1) { userSessionStateService.getUserIdBySession(SESSION_ID) }
        verify { distributorService wasNot Called }
        confirmVerified(userSessionStateService)
    }
}
