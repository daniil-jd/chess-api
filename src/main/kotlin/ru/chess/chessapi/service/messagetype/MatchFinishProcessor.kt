package ru.chess.chessapi.service.messagetype

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.web.websocket.message.MatchFinishedMessageDto
import ru.chess.chessapi.web.websocket.message.enums.MessageType

@Service
class MatchFinishProcessor(
    private val distributorService: DistributorService,
    private val wsSender: WsSender,
) {

    private val logger = KotlinLogging.logger {}

    fun process(message: MatchFinishedMessageDto, session: WebSocketSession) {
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
        wsSender.sendMessageIfSessionExist(userToSend.id!!, messageToAnotherUser)
    }
}