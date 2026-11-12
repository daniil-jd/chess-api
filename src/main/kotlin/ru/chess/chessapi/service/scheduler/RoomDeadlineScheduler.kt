package ru.chess.chessapi.service.scheduler

import net.javacrumbs.shedlock.core.LockAssert
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import ru.chess.chessapi.service.RoomService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.service.sessions.RoomAcceptedMessagesStateService
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.utils.winnerSideForExpiredRoom
import ru.chess.chessapi.web.websocket.message.MatchFinishedMessageDto
import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.SideType

@Service
@ConditionalOnProperty("scheduling.deadline.enabled", havingValue = "true")
class RoomDeadlineScheduler(
    private val roomService: RoomService,
    private val wsSender: WsSender,
    private val roomAcceptedMessagesStateService: RoomAcceptedMessagesStateService,
    private val userSessionStateService: UserSessionStateService,
) {

    @Scheduled(fixedDelayString = "\${scheduling.deadline.fixed-delay-ms}")
    @SchedulerLock(name = "deadline-scheduler")
    fun start() {
        LockAssert.assertLocked()
        val expiredRooms = roomService.updateStatusIfDeadlineAtExpired()

        expiredRooms.forEach { room ->
            val winnerSide = room.winnerSideForExpiredRoom()

            val message = MatchFinishedMessageDto(
                messageType = MessageType.MATCH_FINISHED_WS,
                room = room.id!!,
                winnerSide = winnerSide,
                finishType = FinishType.TIMEOUT
            )
            val user1 = room.user1
            val user2 = room.user2

            val session1 = userSessionStateService.getByUserId(user1.id)
            val session2 = userSessionStateService.getByUserId(user2.id)

            if (session1 != null) {
                wsSender.sendMessage(user1.id!!, message, session1)
            }
            if (session2 != null) {
                wsSender.sendMessage(user2.id!!, message, session2)
            }

            roomAcceptedMessagesStateService.clearByRoomId(room.id!!)
        }

    }
}