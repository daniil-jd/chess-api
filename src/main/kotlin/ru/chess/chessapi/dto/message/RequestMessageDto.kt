package ru.chess.chessapi.dto.message

import ru.chess.chessapi.dto.message.enums.MessageType
import ru.chess.chessapi.dto.message.enums.SideType

data class RequestMessageDto(
    val messageType: MessageType,
    val user: String? = null,
    val side: SideType
): MessageDto
