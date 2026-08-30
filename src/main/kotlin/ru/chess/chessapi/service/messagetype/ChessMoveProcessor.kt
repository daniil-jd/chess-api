package ru.chess.chessapi.service.messagetype

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.web.websocket.message.MoveMessageDto
import ru.chess.chessapi.web.websocket.message.enums.MessageType

@Service
class ChessMoveProcessor(
    private val distributorService: DistributorService,
    private val wsSender: WsSender,
) {

    private val logger = KotlinLogging.logger {}

    fun process(message: MoveMessageDto, session: WebSocketSession) {
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
        wsSender.sendMessageIfSessionExist(userToSend.id!!, messageToAnotherUser)
    }

}