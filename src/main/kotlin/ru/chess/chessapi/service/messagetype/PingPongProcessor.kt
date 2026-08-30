package ru.chess.chessapi.service.messagetype

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.utils.preparePongMessage
import ru.chess.chessapi.web.websocket.message.Ping

@Service
class PingPongProcessor(
    private val distributorService: DistributorService,
    private val userSessionStateService: UserSessionStateService,
    private val wsSender: WsSender,
) {

    private val logger = KotlinLogging.logger {}

    fun process(message: Ping, session: WebSocketSession) {
        val room = distributorService.findRoomById(message.roomId)

        val pongMessage = preparePongMessage(
            room = room,
            userId = message.backendUserId,
            isUser1Online = userSessionStateService.getByUserId(room.user1.id!!) != null,
            isUser2Online = userSessionStateService.getByUserId(room.user2.id!!) != null,
        )
        wsSender.sendMessageIfSessionExist(message.backendUserId, pongMessage)
    }

}