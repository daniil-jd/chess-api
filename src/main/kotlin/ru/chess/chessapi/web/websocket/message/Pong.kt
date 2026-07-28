package ru.chess.chessapi.web.websocket.message

import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.PingPongStatus
import java.util.*

data class Pong(
    val messageType: MessageType,
    val backendUserId: UUID,
    val roomId: UUID,
    val whiteStatus: PingPongStatus,
    val blackStatus: PingPongStatus
): MessageDto
