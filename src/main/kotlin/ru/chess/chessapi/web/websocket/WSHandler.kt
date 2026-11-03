package ru.chess.chessapi.web.websocket

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.socket.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import ru.chess.chessapi.auth.*
import ru.chess.chessapi.service.UserService
import ru.chess.chessapi.service.MessageConvertorService
import ru.chess.chessapi.service.messagetype.CancelMatchProcessor
import ru.chess.chessapi.service.messagetype.ChessMoveProcessor
import ru.chess.chessapi.service.messagetype.MatchFinishProcessor
import ru.chess.chessapi.service.messagetype.PingPongProcessor
import ru.chess.chessapi.service.messagetype.RequestForRoomProcessor
import ru.chess.chessapi.service.messagetype.WsRetryProcessor
import ru.chess.chessapi.service.sessions.SessionStateService
import ru.chess.chessapi.service.sessions.SessionsAggregatorService
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.web.websocket.message.*

@Component
class WSHandler(
    private val sessionStateService: SessionStateService,
    private val sessionsAggregatorService: SessionsAggregatorService,
    private val userSessionStateService: UserSessionStateService,
    private val messageConvertorService: MessageConvertorService,
    private val cancelMatchProcessor: CancelMatchProcessor,
    private val chessMoveProcessor: ChessMoveProcessor,
    private val matchFinishProcessor: MatchFinishProcessor,
    private val pingPongProcessor: PingPongProcessor,
    private val requestForRoomProcessor: RequestForRoomProcessor,
    private val wsRetryProcessor: WsRetryProcessor,
    private val mapper: ObjectMapper,
    private val authState: WebSocketAuthState,
    private val access: PlayerAccessGuard,
    private val users: UserService
) : WebSocketHandler {

    private val logger = KotlinLogging.logger {}

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessionStateService.add(session)
    }

    override fun handleMessage(session: WebSocketSession, webSocketMessage: WebSocketMessage<*>) {
        if (webSocketMessage !is TextMessage) return
        val previousContext = SecurityContextHolder.getContext()
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext())
        try {
            val json = try { mapper.readTree(webSocketMessage.payload) } catch (_: Exception) {
                throw AuthException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "Invalid JSON message")
            }
            if (json?.get("messageType")?.asText() == "AUTH") {
                val token = json.get("sessionToken")?.takeIf { it.isTextual }?.asText()
                    ?: throw AuthException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "A session token is required")
                val principal = authState.bind(session, token)
                synchronized(session) {
                    session.sendMessage(TextMessage(mapper.writeValueAsString(mapOf(
                        "messageType" to "AUTH_OK", "backendUserId" to principal.userId,
                        "authSessionId" to principal.authSessionId, "sessionExpiresAt" to principal.expiresAt
                    ))))
                }
                return
            }
            val principal = authState.principal(session)
            if (principal != null) {
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(principal, null, emptyList())
                json?.get("backendUserId")?.takeIf { it.isTextual && it.asText().isNotBlank() }?.let {
                    if (it.asText() != principal.userId.toString()) {
                        throw AuthException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Profile does not belong to this session")
                    }
                }
            }
            val message = messageConvertorService.convertMessageDtoFromTextMessage(webSocketMessage)
            if (message == null) {
                logger.warn { "Unrecognized WebSocket message in session ${session.id}" }
                return
            }
            logger.debug { "Received ${message.messageType} in session ${session.id}" }

            when (message) {
                is RequestForRoomMessageDto -> {
                    // для этого типа возможна неактивная сессия
                    requestForRoomProcessor.process(message, session)
                }

                is RequestForWsRetryDto -> {
                    // для этого типа возможна неактивная сессия
                    wsRetryProcessor.process(message, session)
                }

                else -> {
                    // для остальных типов сообщений сессия должна быть активна
                    val userId = userSessionStateService.getUserIdBySession(session.id) ?: return
                    val user = users.findById(userId) ?: return
                    access.requireUser(user)

                    when(message) {
                        is MoveMessageDto -> chessMoveProcessor.process(message, session)

                        is MatchFinishedMessageDto -> matchFinishProcessor.process(message, session)

                        is RequestForRoomCancelDto -> cancelMatchProcessor.process(message, session)

                        is Ping -> pingPongProcessor.process(message, session)

                        else -> logger.debug { "Unexpected message type ${message.messageType}" }
                    }
                }
            }
        } catch (e: AuthException) {
            try {
                if (session.isOpen) synchronized(session) {
                    session.sendMessage(TextMessage(mapper.writeValueAsString(mapOf(
                        "messageType" to "AUTH_ERROR", "code" to e.code, "message" to e.message
                    ))))
                }
            } finally { authState.close(session) }
        } finally {
            SecurityContextHolder.setContext(previousContext)
        }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        // не обрабатываем разрыв игрока тут
        logger.error { "ws, transport error, session - ${session.id}, principal - ${session.principal?.name}, exception - ${exception.message}" }
        sessionsAggregatorService.removeSessionFromMaps(session, CloseStatus.SERVER_ERROR)
        authState.forget(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
        // обрабатываем разрыв соединения тут
        if (closeStatus.equalsCode(CloseStatus.SERVER_ERROR)) {
            logger.error { "session (${session.id}) already closed before closing." }
        }
        logger.error { "afterConnectionClosed, ws session (${session.id}) is closed, status: $closeStatus" }

        sessionsAggregatorService.removeSessionFromMaps(session, closeStatus)
        authState.forget(session)
    }

    override fun supportsPartialMessages(): Boolean = false
}
