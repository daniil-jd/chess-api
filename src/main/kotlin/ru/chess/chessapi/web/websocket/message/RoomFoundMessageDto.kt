package ru.chess.chessapi.web.websocket.message

import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.time.Instant
import java.util.UUID

data class RoomFoundMessageDto(
    override val messageType: MessageType = MessageType.ROOM_FOUND,
    val backendUserId: UUID,
    val room: UUID,
    val opponentName: String,
    val playerSide: SideType,
    val sessionId: String,
    val matchServerTimeMs: Instant
): MessageDto
