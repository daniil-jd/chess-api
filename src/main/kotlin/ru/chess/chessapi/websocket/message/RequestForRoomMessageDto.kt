package ru.chess.chessapi.websocket.message

import ru.chess.chessapi.websocket.message.enums.MessageType
import ru.chess.chessapi.websocket.message.enums.SideType
import java.util.UUID

data class RequestForRoomMessageDto(
    val messageType: MessageType,
    val signature: String?,
    val backendUserId: UUID?,
    val username: String,
    val side: SideType
): MessageDto
