package ru.chess.chessapi.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.socket.*
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.MessageConvertorService
import ru.chess.chessapi.websocket.message.*
import ru.chess.chessapi.websocket.message.enums.MessageType
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Component
class WSHandler(
    private val mapper: ObjectMapper,
    private val messageConvertorService: MessageConvertorService,
    private val distributorService: DistributorService
) : WebSocketHandler {

    private val logger = KotlinLogging.logger {}

    private var sessions = ConcurrentHashMap<String, WebSocketSession>()
    private var userIdToSessions = ConcurrentHashMap<UUID, WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions[session.id] = session
    }

    override fun handleMessage(session: WebSocketSession, webSocketMessage: WebSocketMessage<*>) {
        if (webSocketMessage is TextMessage) {
            logger.info { "receive text message: ${webSocketMessage.payload}" }

            // todo: log before this block
            when (val message = messageConvertorService.convertMessageDtoFromTextMessage(webSocketMessage)) {
                is RequestMessageDto -> {
                    logger.info { "received message type: ${message.messageType}, message body: $message" }
                    val userRoomCandidate = distributorService.createUserRoomCandidate(message)
                    logger.info {
                        "created user-room-candidate, " +
                            "id: ${userRoomCandidate.id}, " +
                            "user: ${userRoomCandidate.user}, " +
                            "actual until: ${userRoomCandidate.actualUntil}"
                    }
                    putDefaultPrincipalToSessionIfNotExist(session, userRoomCandidate.user.id!!)
                }

                is MoveMessageDto -> {
                    logger.info { "received message type: ${message.messageType}, message body: $message" }
                    val userToSend = distributorService.updateRoomHistoryAndReturnAnotherUser(
                        message.room, message.sideOfMove, message.move
                    )
                    logger.info { "found another user in room (room id: ${message.room}), another user: $userToSend" }
                    val messageToAnotherUser = MoveMessageDto(
                        messageType = MessageType.CHESS_MOVE,
                        room = message.room,
                        sideOfMove = message.sideOfMove,
                        move = message.move,
                        promotionType = message.promotionType
                    )
                    userIdToSessions.forEach { (userId, wsSession) ->
                        if (userId == userToSend.id) {
                            sendMessageToAnotherUser(messageToAnotherUser, userToSend, wsSession)
                        }
                    }
                }

                is MatchFinishedMessageDto -> {
                    logger.info { "received message type: ${message.messageType}, message body: $message" }
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
                    userIdToSessions.forEach { (userId, wsSession) ->
                        if (userId == userToSend.id) {
                            sendMessageToAnotherUser(messageToAnotherUser, userToSend, wsSession)
                        }
                    }
                }

                is RequestForRoomCancelDto -> {
                    logger.info { "received message type: ${message.messageType}, message body: $message" }
                    if (message.messageType == MessageType.REQUEST_FOR_ROOM_CANCEL) {
                        val userId = getUserIdBySession(session)
                        if (userId != null) {
                            distributorService.cancelRoomCandidate(userId)
                        } else {
                            logger.warn { "Empty userId, can't find by session." }
                        }
                    }
                }

                else -> {
                    logger.info { "Can't determine type of message: $webSocketMessage" }
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
            try {
                if (userId == user1.id) {
                    val message = RoomFoundMessageDto(
                        messageType = MessageType.ROOM_FOUND,
                        room = room.id!!,
                        opponentName = user2.username,
                        playerSide = user1Side
                    )
                    sendMessageToAnotherUser(message, user1, wsSession)
                } else if (userId == user2.id) {
                    val message = RoomFoundMessageDto(
                        messageType = MessageType.ROOM_FOUND,
                        room = room.id!!,
                        opponentName = user1.username,
                        playerSide = user2Side
                    )
                    sendMessageToAnotherUser(message, user2, wsSession)
                }
            } catch (ex: IOException) {
                logger.error(ex) { "Some exception during sending through web socket: ${ex.message}" }
            }
        }
    }

    private fun sendMessageToAnotherUser(message: MessageDto, anotherUser: UserEntity, wsSession: WebSocketSession) {
        wsSession.sendMessage(TextMessage(mapper.writeValueAsString(message)))
        logger.info {
            "successfully sent message to another user: $message, " +
                "another user: $anotherUser"
        }
    }

    private fun putDefaultPrincipalToSessionIfNotExist(session: WebSocketSession, userId: UUID) {
        userIdToSessions.putIfAbsent(userId, session)
    }

    private fun getUserIdBySession(session: WebSocketSession): UUID? {
        logger.info { "trying to find userId by session: " }
        userIdToSessions.forEach { (userId, wbSession) ->
            if (session == wbSession) {
                return userId
            }
        }
        return null
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        throw Exception("ws, transport error, session - ${session.id}, principal - ${session.principal?.name}, exception - ${exception.message}")
    }

    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
        if (closeStatus.equalsCode(CloseStatus.SERVER_ERROR)) {
            logger.error { "session (${session.id}) already closed before closing." }
        }
//        sessions.remove(session.id)
//        session.close(closeStatus)

        val userIdToSessionIterator = userIdToSessions.iterator()
        while (userIdToSessionIterator.hasNext()) {
            val s = userIdToSessionIterator.next()
            if (s.value.id == session.id) {
                userIdToSessionIterator.remove()
                session.close(closeStatus)
                break
            }
        }
    }

    override fun supportsPartialMessages(): Boolean = true
}