package ru.chess.chessapi.web.websocket.message

import com.fasterxml.jackson.annotation.JsonProperty
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.PromotionType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.util.UUID

data class MoveMessageDto(
    override val messageType: MessageType,
    val backendUserId: UUID? = null,
    val room: UUID,
    val sideOfMove: SideType,
    val move: String,
    val moveId: UUID,
    @JsonProperty(required = true)
    val ply: Int,
    val moveForHistory: String? = null,
    val promotionType: PromotionType? = null
): MessageDto
