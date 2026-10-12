package ru.chess.chessapi.web.websocket.message

import com.fasterxml.jackson.annotation.JsonAlias
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import java.util.*

data class RequestForWsRetryDto(
    override val messageType: MessageType,
    val backendUserId: UUID?,
    val sessionId: String?,
    @JsonAlias("room")
    val roomId: UUID? = null
): MessageDto
