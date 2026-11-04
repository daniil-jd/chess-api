package ru.chess.chessapi.service.sessions

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
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.TestData.Sessions.OLD_SESSION_ID
import ru.chess.chessapi.TestData.Sessions.SESSION_ID
import ru.chess.chessapi.TestData.Users.USER_ID_1

@ExtendWith(MockKExtension::class)
class SessionsAggregatorServiceTest {
    @InjectMockKs
    private lateinit var service: SessionsAggregatorService
    @MockK(relaxUnitFun = true)
    private lateinit var userSessionStateService: UserSessionStateService
    @MockK(relaxUnitFun = true)
    private lateinit var sessionStateService: SessionStateService
    @MockK(relaxUnitFun = true)
    private lateinit var session: WebSocketSession

    @Test
    fun `removeOldSessionFromStates - removes known session from both states`() {
        every { userSessionStateService.getUserIdBySession(OLD_SESSION_ID) } returns USER_ID_1

        service.removeOldSessionFromStates(OLD_SESSION_ID)

        verify(exactly = 1) {
            userSessionStateService.getUserIdBySession(OLD_SESSION_ID)
            userSessionStateService.removeByUserId(USER_ID_1)
            sessionStateService.remove(OLD_SESSION_ID)
        }
        confirmVerified(userSessionStateService, sessionStateService)
    }

    @Test
    fun `removeOldSessionFromStates - unknown session does not change states`() {
        every { userSessionStateService.getUserIdBySession(OLD_SESSION_ID) } returns null

        service.removeOldSessionFromStates(OLD_SESSION_ID)

        verify(exactly = 1) { userSessionStateService.getUserIdBySession(OLD_SESSION_ID) }
        verify { sessionStateService wasNot Called }
        confirmVerified(userSessionStateService)
    }

    @Test
    fun `removeSessionFromMaps - removes mappings then closes session with supplied status`() {
        val closeStatus = CloseStatus.GOING_AWAY
        every { session.id } returns SESSION_ID
        every { userSessionStateService.getUserIdBySession(SESSION_ID) } returns USER_ID_1

        service.removeSessionFromMaps(session, closeStatus)

        verify(exactly = 1) {
            userSessionStateService.getUserIdBySession(SESSION_ID)
            userSessionStateService.removeByUserId(USER_ID_1)
            sessionStateService.remove(SESSION_ID)
            session.close(closeStatus)
        }
        verifyOrder {
            userSessionStateService.removeByUserId(USER_ID_1)
            sessionStateService.remove(SESSION_ID)
            session.close(closeStatus)
        }
        confirmVerified(userSessionStateService, sessionStateService)
    }

    @Test
    fun `removeSessionFromMaps - unknown session is not closed`() {
        every { session.id } returns SESSION_ID
        every { userSessionStateService.getUserIdBySession(SESSION_ID) } returns null

        service.removeSessionFromMaps(session, CloseStatus.NORMAL)

        verify(exactly = 1) { userSessionStateService.getUserIdBySession(SESSION_ID) }
        verify(exactly = 0) { session.close(any()) }
        verify { sessionStateService wasNot Called }
        confirmVerified(userSessionStateService)
    }
}
