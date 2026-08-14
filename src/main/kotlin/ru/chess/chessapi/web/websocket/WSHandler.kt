package ru.chess.chessapi.web.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.socket.*
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.MessageConvertorService
import ru.chess.chessapi.utils.preparePongMessage
import ru.chess.chessapi.web.websocket.message.*
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class WSHandler(
    private val mapper: ObjectMapper,
    private val messageConvertorService: MessageConvertorService,
    private val distributorService: DistributorService
) : WebSocketHandler {

    private val logger = KotlinLogging.logger {}

    private var sessions = ConcurrentHashMap<String, WebSocketSession>()
    private var userIdToSessions = ConcurrentHashMap<UUID, WebSocketSession>()
    private var userIdToMessagesNotSend = ConcurrentHashMap<UUID, CopyOnWriteArrayList<MessageDto>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions[session.id] = session
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
            // todo подумать как переделать с messageType вместо проверки класса
            if (message is RequestForRoomMessageDto) {
                // для этого типа возможна неактивная сессия
                val userRoomCandidate = distributorService.createUserRoomCandidate(message)
                logger.info {
                    "created user-room-candidate, " +
                        "id: ${userRoomCandidate.id}, " +
                        "user: ${userRoomCandidate.user}, " +
                        "actual until: ${userRoomCandidate.activeUntil}"
                }
                saveSessionAndSendMessage(userRoomCandidate.user.id!!, session)

                // create room if it possible
                val rooms = distributorService.searchCandidatesAndCreateRooms()
                rooms.forEach { sendRoomCreatedMessage(it) }
            } else if (message is RequestForWsRetryDto) {
                // для этого типа возможна неактивная сессия
                // в случае проблем с ws сессией, попытки восстановить сессию
                val existedSession = userIdToSessions[message.backendUserId!!]
                if (existedSession != null) {
                    logger.warn {
                        "WS_RETRY, session for user: ${message.backendUserId} exist, session $existedSession will be removed"
                    }
                    removeOldSessionFromMaps(existedSession.id)
                    logger.warn {
                        "WS_RETRY, session for user: ${message.backendUserId} removed"
                    }
                }

                val room = distributorService.findNotFinishedRoomByUserId(message.backendUserId) // todo потенциальная проблема
                logger.info {
                    "find not finished room (roomId: ${room.id}) for user (userId: ${message.backendUserId}"
                }
                saveSessionAndSendMessage(message.backendUserId, session)
                // рассылка потерянных сообщений
                userIdToSessions[message.backendUserId]?.let {
                    userIdToMessagesNotSend[message.backendUserId]?.forEach { unsentMsg ->
                        sendMessage(
                            message.backendUserId,
                            unsentMsg,
                            it
                        )
                    } ?: logger.info {
                        "WS_RETRY, there is no unsent messages for retried user, userId = ${message.backendUserId}"
                    }
                } ?: {
                    logger.warn {
                        "WS_RETRY, error while sending messages to retried user, userId = ${message.backendUserId}"
                    }
                }
            } else {
                // для остальных типов сообщений сессия должна быть активна
                if (!isUserSessionActive(session, webSocketMessage.payload)) return

                when(message) {
                    is MoveMessageDto -> {
                        val userToSend = distributorService.updateRoomHistoryAndReturnAnotherUser(
                            message.room, message.sideOfMove, message.move, message.promotionType
                        )
                        logger.info { "found another user in room (room id: ${message.room}), another user: $userToSend" }
                        val messageToAnotherUser = MoveMessageDto(
                            messageType = MessageType.CHESS_MOVE,
                            backendUserId = userToSend.id!!,
                            room = message.room,
                            sideOfMove = message.sideOfMove,
                            move = message.move,
                            promotionType = message.promotionType
                        )
                        sendMessageIfSessionExist(userToSend.id!!, messageToAnotherUser)
                    }

                    is MatchFinishedMessageDto -> {
                        val userToSend = distributorService.updateRoomHistoryAndReturnAnotherUserWhenMatchIsOver(
                            message.room, message.winnerSide, message.finishType
                        )
                        logger.info { "found another user in room (room id: ${message.room}), another user: $userToSend" }
                        val messageToAnotherUser = MatchFinishedMessageDto(
                            messageType = MessageType.MATCH_FINISHED,
                            room = message.room,
                            winnerSide = message.winnerSide,
                            finishType = message.finishType
                        )
                        sendMessageIfSessionExist(userToSend.id!!, messageToAnotherUser)
                    }

                    is RequestForRoomCancelDto -> {
                        if (message.messageType == MessageType.REQUEST_FOR_ROOM_CANCEL) {
                            val userId = getUserIdBySession(session)
                            if (userId != null) {
                                distributorService.cancelRoomCandidate(userId)
                            } else {
                                logger.warn { "Empty userId, can't find by session." }
                            }
                        }
                    }

                    is Ping -> {
                        // опрос клиента, статусы игроков в партии
                        val room = distributorService.findRoomById(message.roomId)

                        val pongMessage = preparePongMessage(
                            room = room,
                            userId = message.backendUserId,
                            isUser1Online = userIdToSessions[room.user1.id!!] != null,
                            isUser2Online = userIdToSessions[room.user2.id!!] != null,
                        )
                        sendMessageIfSessionExist(message.backendUserId, pongMessage)
                    }
                    else -> {
                        logger.info { "Can't determine type of message: $webSocketMessage" }
                    }
                }
            }
        }
    }

    fun sendRoomCreatedMessage(room: RoomEntity) {
        val user1 = room.user1
        val user1Side = room.user1Side
        val user2 = room.user2
        val user2Side = room.user2Side

        userIdToSessions.forEach { (userId, wsSession) ->
            if (userId == user1.id) {
                val message = RoomFoundMessageDto(
                    messageType = MessageType.ROOM_FOUND,
                    backendUserId = userId,
                    room = room.id!!,
                    opponentName = user2.username,
                    playerSide = user1Side,
                    sessionId = wsSession.id
                )
                sendMessage(user1.id!!, message, wsSession)
            } else if (userId == user2.id) {
                val message = RoomFoundMessageDto(
                    messageType = MessageType.ROOM_FOUND,
                    backendUserId = userId,
                    room = room.id!!,
                    opponentName = user1.username,
                    playerSide = user2Side,
                    sessionId = wsSession.id
                )
                sendMessage(user2.id!!, message, wsSession)
            }
        }
    }

    private fun sendMessageIfSessionExist(userIdToSend: UUID, message: MessageDto) {
        userIdToSessions[userIdToSend]?.let {
            sendMessage(userIdToSend, message, it)
        } ?: run {
            // в случае ошибки сохраняем сообщения и отправляем позже
            logger.warn {
                "session for userId: $userIdToSend not found. " +
                    "need to reconnect via ws for user"
            }
            userIdToMessagesNotSend.computeIfAbsent(userIdToSend) { CopyOnWriteArrayList() }.add(message)
        }
    }

    private fun sendMessage(userId: UUID, message: MessageDto, wsSession: WebSocketSession) {
        try {
            wsSession.sendMessage(TextMessage(mapper.writeValueAsString(message)))
        } catch (ex: IOException) {
            logger.warn {
                "can't send message $message to user ($userId), something wrong with ws session " +
                        "(id: ${wsSession.id}, is open: ${wsSession.isOpen}), error: ${ex.message}"
            }
            // в случае ошибки отправки сохраняем сообщение для попытки ретрая в будущем
            userIdToMessagesNotSend.computeIfAbsent(userId) { CopyOnWriteArrayList() }.add(message)
        }
        logger.info { "successfully sent message to user: $message, user: $userId" }
    }

    private fun saveSessionAndSendMessage(userId: UUID, wsSession: WebSocketSession, ) {
        putDefaultPrincipalToSessionIfNotExist(wsSession, userId)
        val message = NewWsSession(
            messageType = MessageType.NEW_WS,
            backendUserId = userId,
            sessionId = wsSession.id
        )
        sendMessage(userId, message, wsSession)
    }

    private fun putDefaultPrincipalToSessionIfNotExist(wsSession: WebSocketSession, userId: UUID) {
        logger.info { "save session (${wsSession.id}) for user: $userId" }
        userIdToSessions.putIfAbsent(userId, wsSession)
    }

    private fun getUserIdBySession(wsSession: WebSocketSession): UUID? {
        logger.info { "trying to find userId by session: ${wsSession.id}" }
        userIdToSessions.forEach { (userId, wbSession) ->
            if (wsSession == wbSession) {
                return userId
            }
        }
        return null
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        // не обрабатываем разрыв игрока тут
        logger.error { "ws, transport error, session - ${session.id}, principal - ${session.principal?.name}, exception - ${exception.message}" }
        removeSessionFromMaps(session, CloseStatus.SERVER_ERROR)
    }

    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
        // обрабатываем разрыв соединения тут
        if (closeStatus.equalsCode(CloseStatus.SERVER_ERROR)) {
            logger.error { "session (${session.id}) already closed before closing." }
        }
        logger.error { "afterConnectionClosed, ws session (${session.id}) is closed, status: $closeStatus" }

        removeSessionFromMaps(session, closeStatus)
    }

    private fun removeOldSessionFromMaps(oldSessionId: String) {
        val userIdToSessionIterator = userIdToSessions.iterator()
        while (userIdToSessionIterator.hasNext()) {
            val userToSession = userIdToSessionIterator.next()
            if (userToSession.value.id == oldSessionId) {
                userIdToSessionIterator.remove()
                sessions.remove(oldSessionId)
                break
            }
        }
    }

    private fun removeSessionFromMaps(session: WebSocketSession, closeStatus: CloseStatus) {
        val userIdToSessionIterator = userIdToSessions.iterator()
        while (userIdToSessionIterator.hasNext()) {
            val userToSession = userIdToSessionIterator.next()
            if (userToSession.value.id == session.id) {
                userIdToSessionIterator.remove()
                sessions.remove(session.id)
                session.close(closeStatus)
                break
            }
        }
    }

    // проверка, что не шлют сообщения из "второго окна", из "старой сессии", новые сессии создаются при WS_RETRY
    private fun isUserSessionActive(session: WebSocketSession, message: String): Boolean {
        return if (getUserIdBySession(session) == null) {
            logger.warn {
                "some user use inactive session: ${session.id}, message: $message, stop processing this message"
            }
            false
        } else {
            true
        }
    }

    override fun supportsPartialMessages(): Boolean = true
}