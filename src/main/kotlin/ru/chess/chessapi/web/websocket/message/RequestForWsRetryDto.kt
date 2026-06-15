package ru.chess.chessapi.web.websocket.message

import ru.chess.chessapi.web.websocket.message.enums.MessageType
import java.util.*

data class RequestForWsRetryDto(
    val messageType: MessageType,
    val backendUserId: UUID?
): MessageDto
