package ru.chess.chessapi.dto.message

import ru.chess.chessapi.dto.message.enums.MessageType
import ru.chess.chessapi.dto.message.enums.PromotionType
import ru.chess.chessapi.dto.message.enums.SideType
import java.util.UUID

data class MoveMessageDto(
    val messageType: MessageType,
    val room: UUID,
    val sideOfMove: SideType,
    val move: String,
    val promotionType: PromotionType?
): MessageDto
