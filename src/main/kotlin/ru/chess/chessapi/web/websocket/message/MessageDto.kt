package ru.chess.chessapi.web.websocket.message

import ru.chess.chessapi.web.websocket.message.enums.MessageType

sealed interface MessageDto {
    val messageType: MessageType
}