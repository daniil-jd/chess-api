package ru.chess.chessapi.web.websocket.message

import ru.chess.chessapi.web.websocket.message.enums.MessageType
import java.util.UUID

data class Ping(
    override val messageType: MessageType,
    val backendUserId: UUID,
    val roomId: UUID
): MessageDto
