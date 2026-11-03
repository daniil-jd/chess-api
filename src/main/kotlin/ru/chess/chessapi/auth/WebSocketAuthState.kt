package ru.chess.chessapi.auth

import mu.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.service.sessions.SessionsAggregatorService
import ru.chess.chessapi.service.sessions.UserSessionStateService
import java.time.Clock
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class WebSocketAuthState(
    private val auth: AuthService,
    private val sessions: SessionsAggregatorService,
    private val gameSessions: UserSessionStateService,
    private val clock: Clock
) {
    private val logger = KotlinLogging.logger {}
    private class Binding(val socket: WebSocketSession, val token: String, val principal: AuthPrincipal)
    private val bindings = ConcurrentHashMap<String, Binding>()

    fun bind(socket: WebSocketSession, token: String): AuthPrincipal {
        val principal = auth.authenticate(token)
        val owner = bindings[socket.id]?.principal?.userId ?: gameSessions.getUserIdBySession(socket.id)
        if (owner != null && owner != principal.userId) {
            throw AuthException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Reconnect before switching profiles")
        }
        bindings[socket.id] = Binding(socket, token, principal)
        return principal
    }

    fun principal(socket: WebSocketSession): AuthPrincipal? = bindings[socket.id]?.let { auth.authenticate(it.token) }

    fun canSend(socket: WebSocketSession, userId: UUID): Boolean {
        if (!socket.isOpen) return false
        return try {
            val principal = principal(socket)
            if (principal != null && principal.userId != userId) {
                close(socket)
                false
            } else true
        } catch (_: AuthException) {
            close(socket)
            false
        }
    }

    fun forget(socket: WebSocketSession) { bindings.remove(socket.id) }

    fun close(socket: WebSocketSession) {
        // Keep the binding if closing fails: this socket must never downgrade to legacy access.
        try { sessions.removeSessionFromMaps(socket, CloseStatus.POLICY_VIOLATION) }
        catch (_: IOException) { logger.warn { "Unable to close WebSocket ${socket.id}" } }
        catch (_: IllegalStateException) { logger.warn { "Unable to close WebSocket ${socket.id}" } }
        finally { if (!socket.isOpen) forget(socket) }
    }

    @EventListener
    fun sessionsRevoked(event: AuthSessionsRevoked) {
        bindings.values.filter { it.principal.authSessionId in event.sessionIds }.forEach { close(it.socket) }
    }

    @Scheduled(fixedDelay = 5000)
    fun closeExpired() {
        val now = clock.instant()
        bindings.values.filter { !it.principal.expiresAt.isAfter(now) }.forEach { close(it.socket) }
    }
}
