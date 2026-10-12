package ru.chess.chessapi.service.sessions

import org.springframework.stereotype.Service
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession

@Service
class SessionsAggregatorService(
    private val userSessionStateService: UserSessionStateService,
    private val sessionStateService: SessionStateService
) {

    fun removeSessionFromMaps(session: WebSocketSession, closeStatus: CloseStatus) {
        sessionStateService.remove(session.id)
        val userId = userSessionStateService.getUserIdBySession(session.id)
        if (userId != null) {
            userSessionStateService.removeByUserId(userId, session)
        }
        if (session.isOpen) session.close(closeStatus)
    }

}
