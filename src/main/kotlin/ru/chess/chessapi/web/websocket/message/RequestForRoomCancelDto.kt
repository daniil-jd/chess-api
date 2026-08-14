package ru.chess.chessapi.web.websocket.message

import ru.chess.chessapi.web.websocket.message.enums.MessageType

data class RequestForRoomCancelDto(
    override val messageType: MessageType
) : MessageDto
