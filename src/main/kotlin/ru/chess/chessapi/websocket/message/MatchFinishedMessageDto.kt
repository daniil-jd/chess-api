package ru.chess.chessapi.websocket.message

import ru.chess.chessapi.websocket.message.enums.MessageType
import ru.chess.chessapi.websocket.message.enums.SideType
import java.util.UUID

data class MatchFinishedMessageDto(
    val messageType: MessageType,
    val room: UUID,
    val winnerSide: SideType,
    val finishType: String
): MessageDto
