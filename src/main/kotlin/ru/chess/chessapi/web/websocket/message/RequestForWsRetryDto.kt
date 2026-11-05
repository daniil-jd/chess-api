package ru.chess.chessapi.web.websocket.message

import ru.chess.chessapi.web.websocket.message.enums.MessageType
import java.util.*

data class RequestForWsRetryDto(
    override val messageType: MessageType,
    val backendUserId: UUID?,
    val sessionId: String?
): MessageDto
