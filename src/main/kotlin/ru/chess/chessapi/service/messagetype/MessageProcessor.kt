package ru.chess.chessapi.service.messagetype

import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.web.websocket.message.MessageDto

interface MessageProcessor<T : MessageDto> {
    fun process(message: T, session: WebSocketSession)
}