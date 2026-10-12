package ru.chess.chessapi.service.messagetype

import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.service.OnlineMatchService
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.web.websocket.message.MoveMessageDto

@Service
class ChessMoveProcessor(
    private val onlineMatchService: OnlineMatchService,
    private val userSessionStateService: UserSessionStateService
) {
    fun process(message: MoveMessageDto, session: WebSocketSession) {
        val userId = userSessionStateService.getUserIdBySession(session.id) ?: return
        onlineMatchService.acceptMove(message, userId)
    }
}
