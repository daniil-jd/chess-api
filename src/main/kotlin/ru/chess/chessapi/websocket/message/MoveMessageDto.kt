package ru.chess.chessapi.websocket.message

import ru.chess.chessapi.websocket.message.enums.MessageType
import ru.chess.chessapi.websocket.message.enums.PromotionType
import ru.chess.chessapi.websocket.message.enums.SideType
import java.util.UUID

data class MoveMessageDto(
    val messageType: MessageType,
    val backendUserId: UUID,
    val room: UUID,
    val sideOfMove: SideType,
    val move: String,
    val promotionType: PromotionType?
): MessageDto
