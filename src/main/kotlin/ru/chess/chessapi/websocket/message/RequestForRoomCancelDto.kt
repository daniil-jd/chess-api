package ru.chess.chessapi.websocket.message

import ru.chess.chessapi.websocket.message.enums.MessageType

data class RequestForRoomCancelDto(
    val messageType: MessageType
) : MessageDto
