package ru.chess.chessapi.service.sessions

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import java.util.UUID

class SessionsAggregatorServiceTest {
    private val userId = UUID.randomUUID()
    private val userSessions = UserSessionStateService()
    private val sessions = mock(SessionStateService::class.java)
    private val aggregator = SessionsAggregatorService(userSessions, sessions)

    @Test
    fun `closing replaced session removes its state and preserves replacement`() {
        val old = session("old")
        val replacement = session("new")
        userSessions.add(userId, old)
        userSessions.add(userId, replacement)

        aggregator.removeSessionFromMaps(old, CloseStatus.NORMAL)

        verify(sessions).remove("old")
        verify(old).close(CloseStatus.NORMAL)
        assertSame(replacement, userSessions.getByUserId(userId))
    }

    @Test
    fun `removal using stale session cannot erase concurrently replaced mapping`() {
        val old = session("old")
        val replacement = session("new")
        userSessions.add(userId, old)
        val resolvedUserId = userSessions.getUserIdBySession("old")!!
        userSessions.add(userId, replacement)

        userSessions.removeByUserId(resolvedUserId, old)

        assertSame(replacement, userSessions.getByUserId(userId))
        aggregator.removeSessionFromMaps(replacement, CloseStatus.NORMAL)
        assertNull(userSessions.getByUserId(userId))
        verify(sessions).remove("new")
        verify(replacement).close(CloseStatus.NORMAL)
    }

    private fun session(id: String): WebSocketSession = mock(WebSocketSession::class.java).also {
        `when`(it.id).thenReturn(id)
        `when`(it.isOpen).thenReturn(true)
    }
}
