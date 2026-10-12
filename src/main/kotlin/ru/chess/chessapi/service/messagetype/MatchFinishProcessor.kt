package ru.chess.chessapi.service.messagetype

import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.service.OnlineMatchService
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.web.websocket.message.MatchFinishedMessageDto

@Service
class MatchFinishProcessor(
    private val onlineMatchService: OnlineMatchService,
    private val userSessionStateService: UserSessionStateService
) {
    fun process(message: MatchFinishedMessageDto, session: WebSocketSession) {
        val userId = userSessionStateService.getUserIdBySession(session.id) ?: return
        onlineMatchService.finishMatch(message, userId)
    }
}
