package ru.chess.chessapi.websocket.message

import ru.chess.chessapi.websocket.message.enums.MessageType
import ru.chess.chessapi.websocket.message.enums.SideType
import java.util.UUID

data class RoomFoundMessageDto(
    val messageType: MessageType,
    val backendUserId: UUID,
    val room: UUID,
    val opponentName: String,
    val playerSide: SideType
): MessageDto
