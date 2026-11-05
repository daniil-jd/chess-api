package ru.chess.chessapi.web.websocket.message

import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.PromotionType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.time.Instant
import java.util.*

data class MoveAcceptedMessage(
    override val messageType: MessageType = MessageType.CHESS_MOVE_ACCEPTED,
    val room: UUID,
    val sideOfMove: SideType,
    val move: String,
    val moveId: UUID,
    // номер полухода игрока в рамках партии
    val ply: Int,
    val promotionType: PromotionType?,
    val turnServerTimeMs: Long
) : MessageDto
