package ru.chess.chessapi.service.messagetype

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.service.sessions.RoomAcceptedMessagesStateService
import ru.chess.chessapi.utils.findAnotherUser
import ru.chess.chessapi.utils.findAnotherUserWhenMatchFinished
import ru.chess.chessapi.utils.findSide
import ru.chess.chessapi.web.websocket.message.MatchFinishedMessageDto
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.FinishType

@Service
class MatchFinishProcessor(
    private val distributorService: DistributorService,
    private val wsSender: WsSender,
    private val roomAcceptedMessagesStateService: RoomAcceptedMessagesStateService,
    private val userSessionStateService: UserSessionStateService
) : MessageProcessor<MatchFinishedMessageDto> {

    private val logger = KotlinLogging.logger {}

    override fun process(message: MatchFinishedMessageDto, session: WebSocketSession) {
        if (message.winnerSide == null && message.finishType != FinishType.DRAW) return
        val userId = userSessionStateService.getUserIdBySession(session.id) ?: return
        val currentRoom = distributorService.findRoomById(message.room) ?: return
        val senderSide = currentRoom.findSide(userId) ?: return
        val room = distributorService.updateRoomHistoryWhenMatchIsOver(
            message.room, message.winnerSide, message.finishType
        )
        val userToSend = if (message.winnerSide == null) room.findAnotherUser(senderSide)
            else room.findAnotherUserWhenMatchFinished(message.winnerSide, message.finishType)
        logger.info { "found another user in room (room id: ${message.room}), another user: $userToSend" }
        val messageToAnotherUser = MatchFinishedMessageDto(
            messageType = MessageType.MATCH_FINISHED,
            room = message.room,
            winnerSide = message.winnerSide,
            finishType = message.finishType
        )
        wsSender.sendMessageIfSessionExist(userToSend.id!!, messageToAnotherUser)

        roomAcceptedMessagesStateService.clearByRoomId(room.id!!)
    }
}