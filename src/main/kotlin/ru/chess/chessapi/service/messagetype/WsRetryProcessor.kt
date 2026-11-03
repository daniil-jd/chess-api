package ru.chess.chessapi.service.messagetype

import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.repository.RoomRepository
import ru.chess.chessapi.service.OnlineMatchService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.web.websocket.message.RequestForWsRetryDto
import ru.chess.chessapi.auth.PlayerAccessGuard
import ru.chess.chessapi.service.UserService
import ru.chess.chessapi.exception.UserDoesNotExistException

@Service
class WsRetryProcessor(
    private val roomRepository: RoomRepository,
    private val onlineMatchService: OnlineMatchService,
    private val wsSender: WsSender,
    private val userService: UserService,
    private val access: PlayerAccessGuard
) {
    fun process(message: RequestForWsRetryDto, session: WebSocketSession) {
        access.requireMatchingId(message.backendUserId)
        val userId = access.current()?.userId ?: message.backendUserId ?: return
        val user = userService.findById(userId) ?: throw UserDoesNotExistException(userId.toString())
        access.requireUser(user)
        val roomId = message.roomId ?: roomRepository
            .findLatestOnlineRoomByUser(userId)?.id
        if (roomId == null) {
            wsSender.saveSessionAndSendNewSessionMessage(userId, session)
            return
        }
        onlineMatchService.reconnect(roomId, userId, session)
    }
}
