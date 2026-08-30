package ru.chess.chessapi.web.websocket

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.socket.*
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.service.DistributorService
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
import ru.chess.chessapi.utils.preparePongMessage
import ru.chess.chessapi.web.websocket.message.*
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import java.io.IOException
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

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
    private val wsRetryProcessor: WsRetryProcessor
) : WebSocketHandler {

    private val logger = KotlinLogging.logger {}

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessionStateService.add(session)
    }

    @Transactional
    override fun handleMessage(session: WebSocketSession, webSocketMessage: WebSocketMessage<*>) {
        if (webSocketMessage is TextMessage) {
            logger.info { "receive text message: ${webSocketMessage.payload}" }
            val message = messageConvertorService.convertMessageDtoFromTextMessage(webSocketMessage)
            if (message == null) {
                logger.warn {
                    "received message, that can't be converted: ${webSocketMessage.payload}"
                }
                return
            }
            logger.info { "received message type: ${message.messageType}, message body: ${webSocketMessage.payload}" }

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
                    if (!userSessionStateService.isUserSessionActive(session, webSocketMessage.payload)) return

                    when(message) {
                        is MoveMessageDto -> chessMoveProcessor.process(message, session)

                        is MatchFinishedMessageDto -> matchFinishProcessor.process(message, session)

                        is RequestForRoomCancelDto -> cancelMatchProcessor.process(message, session)

                        is Ping -> pingPongProcessor.process(message, session)

                        else -> logger.info { "Can't determine type of message: $webSocketMessage" }
                    }
                }
            }
        }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        // не обрабатываем разрыв игрока тут
        logger.error { "ws, transport error, session - ${session.id}, principal - ${session.principal?.name}, exception - ${exception.message}" }
        sessionsAggregatorService.removeSessionFromMaps(session, CloseStatus.SERVER_ERROR)
    }

    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
        // обрабатываем разрыв соединения тут
        if (closeStatus.equalsCode(CloseStatus.SERVER_ERROR)) {
            logger.error { "session (${session.id}) already closed before closing." }
        }
        logger.error { "afterConnectionClosed, ws session (${session.id}) is closed, status: $closeStatus" }

        sessionsAggregatorService.removeSessionFromMaps(session, closeStatus)
    }

    override fun supportsPartialMessages(): Boolean = true
}