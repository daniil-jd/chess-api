package ru.chess.chessapi.websocket.message

import ru.chess.chessapi.websocket.message.enums.MessageType
import ru.chess.chessapi.websocket.message.enums.SideType

data class RequestMessageDto(
    val messageType: MessageType,
    val user: String?,
    val side: SideType
): MessageDto
