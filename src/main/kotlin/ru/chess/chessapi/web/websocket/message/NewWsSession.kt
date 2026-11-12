package ru.chess.chessapi.web.websocket.message

import ru.chess.chessapi.web.websocket.message.enums.MessageType
import java.util.*

data class NewWsSession(
    override val messageType: MessageType,
    val backendUserId: UUID?,
    val sessionId: String?,
    val room: UUID?,
    val turns: List<MoveAcceptedMessage>,
    val matchServerTimeMs: Long?
): MessageDto
