package ru.chess.chessapi.dto.message

import ru.chess.chessapi.dto.message.enums.MessageType
import ru.chess.chessapi.dto.message.enums.SideType
import java.util.UUID

data class RoomFoundMessageDto(
    val messageType: MessageType,
    val room: UUID,
    val opponentName: String,
    val playerSide: SideType
): MessageDto
