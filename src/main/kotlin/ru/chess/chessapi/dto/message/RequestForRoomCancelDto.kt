package ru.chess.chessapi.dto.message

import ru.chess.chessapi.dto.message.enums.MessageType

data class RequestForRoomCancelDto(
    val messageType: MessageType
) : MessageDto
