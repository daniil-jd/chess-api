package ru.chess.chessapi.web.websocket.message

import com.fasterxml.jackson.annotation.JsonFormat
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.PromotionType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.util.UUID

data class MoveAcceptedMessageDto(
    override val messageType: MessageType = MessageType.CHESS_MOVE_ACCEPTED,
    val room: UUID,
    val sideOfMove: SideType,
    val move: String,
    val moveId: UUID,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val ply: Int,
    val promotionType: PromotionType? = null,
    val turnServerTimeMs: Long
) : MessageDto
