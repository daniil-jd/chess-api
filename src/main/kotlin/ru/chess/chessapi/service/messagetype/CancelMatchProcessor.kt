package ru.chess.chessapi.service.messagetype

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.web.websocket.message.RequestForRoomCancelDto

@Service
class CancelMatchProcessor(
    private val distributorService: DistributorService,
    private val userSessionStateService: UserSessionStateService,
    private val wsSender: WsSender,
) {

    private val logger = KotlinLogging.logger {}

    fun process(message: RequestForRoomCancelDto, session: WebSocketSession) {
        val userId = userSessionStateService.getUserIdBySession(session.id)
        if (userId != null) {
            distributorService.cancelRoomCandidate(userId)
        } else {
            logger.warn {
                "REQUEST_FOR_ROOM_CANCEL, nothing found by session - ${session.id}, can't cancel room candidate."
            }
        }
    }
    
    
}