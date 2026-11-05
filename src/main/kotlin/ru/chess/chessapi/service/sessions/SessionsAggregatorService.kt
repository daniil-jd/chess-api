package ru.chess.chessapi.service.sessions

import org.springframework.stereotype.Service
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession

@Service
class SessionsAggregatorService(
    private val userSessionStateService: UserSessionStateService,
    private val sessionStateService: SessionStateService
) {

    fun removeOldSessionFromStates(oldSessionId: String) {
        val userId = userSessionStateService.getUserIdBySession(oldSessionId)
        if (userId != null) {
            userSessionStateService.removeByUserId(userId)
            sessionStateService.remove(oldSessionId)
        }
    }

    fun removeSessionFromMaps(session: WebSocketSession, closeStatus: CloseStatus) {
        val userId = userSessionStateService.getUserIdBySession(session.id)
        if (userId != null) {
            userSessionStateService.removeByUserId(userId)
            sessionStateService.remove(session.id)
            session.close(closeStatus)
        }
    }

}