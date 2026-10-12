package ru.chess.chessapi.service.messagetype

import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.repository.RoomRepository
import ru.chess.chessapi.service.OnlineMatchService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.web.websocket.message.RequestForWsRetryDto

@Service
class WsRetryProcessor(
    private val roomRepository: RoomRepository,
    private val onlineMatchService: OnlineMatchService,
    private val wsSender: WsSender
) {
    fun process(message: RequestForWsRetryDto, session: WebSocketSession) {
        val userId = message.backendUserId ?: return
        val roomId = message.roomId ?: roomRepository
            .findLatestOnlineRoomByUser(userId)?.id
        if (roomId == null) {
            wsSender.saveSessionAndSendNewSessionMessage(userId, session)
            return
        }
        onlineMatchService.reconnect(roomId, userId, session)
    }
}
