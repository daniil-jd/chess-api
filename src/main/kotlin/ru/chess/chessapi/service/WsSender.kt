package ru.chess.chessapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.web.websocket.message.MessageDto
import ru.chess.chessapi.web.websocket.message.MoveAcceptedMessage
import ru.chess.chessapi.web.websocket.message.NewWsSession
import ru.chess.chessapi.web.websocket.message.RoomFoundMessageDto
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import java.io.IOException
import java.time.Instant
import java.util.*

@Service
class WsSender(
    private val mapper: ObjectMapper,
    private val userSessionStateService: UserSessionStateService,
    private val timeService: TimeService
) {

    private val logger = KotlinLogging.logger {}

    fun sendMessage(
        userId: UUID,
        message: MessageDto,
        wsSession: WebSocketSession
    ) {
        try {
            wsSession.sendMessage(TextMessage(mapper.writeValueAsString(message)))
        } catch (ex: IOException) {
            logger.warn {
                "can't send message $message to user ($userId), something wrong with ws session " +
                    "(id: ${wsSession.id}, is open: ${wsSession.isOpen}), error: ${ex.message}"
            }
        }
        logger.info { "successfully sent message to user: $message, user: $userId" }
    }

    fun sendMessageIfSessionExist(userIdToSend: UUID, message: MessageDto) {
        userSessionStateService.getByUserId(userIdToSend)?.let {
            sendMessage(userIdToSend, message, it)
        } ?: run {
            logger.warn {
                "session for userId: $userIdToSend not found. " +
                    "need to reconnect via ws for user"
            }
        }
    }

    fun saveSessionAndSendNewSessionMessage(
        userId: UUID,
        moveAcceptedMessages: List<MoveAcceptedMessage>,
        roomCreatedAt: Instant?,
        session: WebSocketSession
    ) {
        userSessionStateService.add(userId, session)
        val message = NewWsSession(
            messageType = MessageType.NEW_WS,
            backendUserId = userId,
            sessionId = session.id,
            turns = moveAcceptedMessages,
            matchServerTimeMs = roomCreatedAt?.toEpochMilli()
        )
        sendMessage(userId, message, session)
    }

    fun sendRoomCreatedMessage(room: RoomEntity) {
        val user1 = room.user1
        val user1Side = room.user1Side
        val user2 = room.user2
        val user2Side = room.user2Side

        val session1 = userSessionStateService.getByUserId(user1.id!!)
        val session2 = userSessionStateService.getByUserId(user2.id!!)

        val message1 = RoomFoundMessageDto(
            backendUserId = user1.id!!,
            room = room.id!!,
            opponentName = user2.username,
            playerSide = user1Side,
            sessionId = session1!!.id,
            matchServerTimeMs = room.updatedAt ?: timeService.getNowInstant()
        )
        sendMessage(user1.id!!, message1, session1)

        val message2 = RoomFoundMessageDto(
            backendUserId = user2.id!!,
            room = room.id!!,
            opponentName = user1.username,
            playerSide = user2Side,
            sessionId = session2!!.id,
            matchServerTimeMs = room.updatedAt ?: timeService.getNowInstant()
        )
        sendMessage(user2.id!!, message2, session2)
    }

}