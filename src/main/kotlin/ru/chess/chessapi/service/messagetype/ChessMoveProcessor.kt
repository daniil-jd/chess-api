package ru.chess.chessapi.service.messagetype

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.TimeService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.service.sessions.RoomAcceptedMessagesStateService
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.utils.findSide
import ru.chess.chessapi.web.websocket.message.MoveAcceptedMessage
import ru.chess.chessapi.web.websocket.message.MoveMessageDto
import ru.chess.chessapi.web.websocket.message.enums.SideType

@Service
class ChessMoveProcessor(
    private val distributorService: DistributorService,
    private val wsSender: WsSender,
    private val roomAcceptedMessagesStateService: RoomAcceptedMessagesStateService,
    private val timeService: TimeService,
    private val userSessionStateService: UserSessionStateService,
) : MessageProcessor<MoveMessageDto> {

    private val logger = KotlinLogging.logger {}

    override fun process(message: MoveMessageDto, session: WebSocketSession) {
        val room = distributorService.findRoomById(message.room) ?: return
        val userId = userSessionStateService.getUserIdBySession(session.id) ?: return

        if (message.backendUserId != userId) return
        val playerSide = room.findSide(userId) ?: return
        if (message.sideOfMove != playerSide) return
        
        val existingMessage = roomAcceptedMessagesStateService.getMessageByRoomIdAndMoveId(message.room, message.moveId)

        if (existingMessage != null) {
            wsSender.sendMessageIfSessionExist(room.user1.id!!, existingMessage)
            wsSender.sendMessageIfSessionExist(room.user2.id!!, existingMessage)
        } else {
            val expectedPly: Int = (roomAcceptedMessagesStateService.getLastMoveByRoom(room.id!!) ?: 0) + 1
            val expectedSide = if (expectedPly % 2 == 1) SideType.WHITE else SideType.BLACK
            if (message.ply != expectedPly || playerSide != expectedSide) {
                logger.warn { "CHESS_MOVE_ACCEPTED: ignoring invalid move or ply in room ${message.room}" }
                return
            }

            val updatedRoom = distributorService.updateRoomHistory(
                message.room, message.moveForHistory ?: message.move, message.promotionType, message.sideOfMove
            )
            logger.info {
                "try to send CHESS_MOVE_ACCEPTED to users (user1: ${updatedRoom.user1.id}, user2: ${updatedRoom.user2.id}) " +
                        "in room (${updatedRoom.id})"
            }
            val messageToBothUsers = MoveAcceptedMessage(
                room = updatedRoom.id!!,
                sideOfMove = message.sideOfMove,
                move = message.move,
                promotionType = message.promotionType,
                moveId = message.moveId,
                ply = message.ply,
                turnServerTimeMs = updatedRoom.updatedAt?.toEpochMilli() ?: timeService.getNowInstant().toEpochMilli(),
            )
            roomAcceptedMessagesStateService.addMessage(updatedRoom.id!!, messageToBothUsers)

            wsSender.sendMessageIfSessionExist(room.user1.id!!, messageToBothUsers)
            wsSender.sendMessageIfSessionExist(room.user2.id!!, messageToBothUsers)
        }
    }

}