package ru.chess.chessapi.service.messagetype

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.TimeService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.service.sessions.RoomAcceptedMessagesStateService
import ru.chess.chessapi.web.websocket.message.MessageDto
import ru.chess.chessapi.web.websocket.message.MoveAcceptedMessage
import ru.chess.chessapi.web.websocket.message.MoveMessageDto

@Service
class ChessMoveProcessor(
    private val distributorService: DistributorService,
    private val wsSender: WsSender,
    private val roomAcceptedMessagesStateService: RoomAcceptedMessagesStateService,
    private val timeService: TimeService
) : MessageProcessor<MoveMessageDto> {

    private val logger = KotlinLogging.logger {}

    override fun process(message: MoveMessageDto, session: WebSocketSession) {
        val room = distributorService.updateRoomHistory(
            message.room, message.move, message.promotionType, message.sideOfMove
        )
        logger.info {
            "try to send CHESS_MOVE_ACCEPTED to users (user1: ${room.user1.id}, user2: ${room.user2.id}) " +
                "in room (${room.id})"
        }
        val messageToBothUsers = MoveAcceptedMessage(
            room = room.id!!,
            sideOfMove = message.sideOfMove,
            move = message.move,
            promotionType = message.promotionType,
            moveId = message.moveId,
            ply = message.ply,
            turnServerTimeMs = room.updatedAt?.toEpochMilli() ?: timeService.getNowInstant().toEpochMilli(),
        )
        roomAcceptedMessagesStateService.addMessage(room.id!!, messageToBothUsers)

        wsSender.sendMessageIfSessionExist(room.user1.id!!, messageToBothUsers)
        wsSender.sendMessageIfSessionExist(room.user2.id!!, messageToBothUsers)
    }

}