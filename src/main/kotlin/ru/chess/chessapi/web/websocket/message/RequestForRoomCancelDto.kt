package ru.chess.chessapi.web.websocket.message

import ru.chess.chessapi.web.websocket.message.enums.MessageType

data class RequestForRoomCancelDto(
    val messageType: MessageType
) : MessageDto
