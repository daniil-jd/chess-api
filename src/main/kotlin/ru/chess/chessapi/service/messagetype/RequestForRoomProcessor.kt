package ru.chess.chessapi.service.messagetype

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.web.websocket.message.RequestForRoomMessageDto

@Service
class RequestForRoomProcessor(
    private val distributorService: DistributorService,
    private val wsSender: WsSender
) {

    private val logger = KotlinLogging.logger {}

    fun process(message: RequestForRoomMessageDto, session: WebSocketSession) {
        val userRoomCandidate = distributorService.createUserRoomCandidate(message)
        logger.info {
            "created user-room-candidate, " +
                "id: ${userRoomCandidate.id}, " +
                "user: ${userRoomCandidate.user}, " +
                "actual until: ${userRoomCandidate.activeUntil}"
        }
        wsSender.saveSessionAndSendNewSessionMessage(userRoomCandidate.user.id!!, session)

        // create room if it possible
        val rooms = distributorService.searchCandidatesAndCreateRooms()
        rooms.forEach { wsSender.sendRoomCreatedMessage(it) }
    }

}