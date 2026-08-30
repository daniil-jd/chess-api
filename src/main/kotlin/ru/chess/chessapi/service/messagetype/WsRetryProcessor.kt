package ru.chess.chessapi.service.messagetype

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.service.sessions.MessagesNotSendStateService
import ru.chess.chessapi.service.sessions.SessionsAggregatorService
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.web.websocket.message.RequestForWsRetryDto

@Service
class WsRetryProcessor(
    private val distributorService: DistributorService,
    private val wsSender: WsSender,
    private val userSessionStateService: UserSessionStateService,
    private val messagesNotSendStateService: MessagesNotSendStateService,
    private val sessionsAggregatorService: SessionsAggregatorService
) {

    private val logger = KotlinLogging.logger {}

    fun process(message: RequestForWsRetryDto, session: WebSocketSession) {
        // в случае проблем с ws сессией, попытки восстановить сессию
        val existedSession = userSessionStateService.getByUserId(message.backendUserId!!)
        if (existedSession != null) {
            logger.warn {
                "WS_RETRY, session for user: ${message.backendUserId} exist, session $existedSession will be removed"
            }
            sessionsAggregatorService.removeOldSessionFromStates(existedSession.id)
            logger.warn {
                "WS_RETRY, session for user: ${message.backendUserId} removed"
            }
        }

        val room = distributorService.findNotFinishedRoomByUserId(message.backendUserId) // todo потенциальная проблема
        logger.info {
            "find not finished room (roomId: ${room.id}) for user (userId: ${message.backendUserId}"
        }
        wsSender.saveSessionAndSendNewSessionMessage(message.backendUserId, session)
        // рассылка потерянных сообщений
        userSessionStateService.getByUserId(message.backendUserId)?.let {
            messagesNotSendStateService.getMessagesByUserId(message.backendUserId).forEach { unsentMsg ->
                wsSender.sendMessage(
                    message.backendUserId,
                    unsentMsg,
                    it
                )
            }
        } ?: {
            logger.warn {
                "WS_RETRY, error while sending messages to retried user, userId = ${message.backendUserId}"
            }
        }
    }

}