package ru.chess.chessapi.service.scheduler

import mu.KotlinLogging
import net.javacrumbs.shedlock.core.LockAssert
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.web.websocket.WSHandler

@Service
class UserRoomCandidateSchedulerService(
    private val distributorService: DistributorService,
    private val wsHandler: WSHandler
) {

    private val logger = KotlinLogging.logger {}

    companion object {
        const val SCHEDULER_LOCK_INTERVAL = "5s"
    }

    @Transactional
    @Scheduled(
            fixedRateString = "PT15S",
            initialDelayString = "PT10S"
    )
    @SchedulerLock(name = "UserRoomCandidateSchedulerService", lockAtMostFor = SCHEDULER_LOCK_INTERVAL) //todo создавать комнату, когда создается кандидат сразу
    fun searchCandidates() {
        LockAssert.assertLocked()
        val rooms = distributorService.searchCandidatesAndCreateRooms()
        rooms.forEach {
            wsHandler.sendRoomCreatedMessage(it)
        }
    }
}

//    @Transactional
//    @Scheduled(cron = "*/8 * * * * *", zone = "Europe/Moscow")
//    @SchedulerLock(name = "UserRoomCandidateSchedulerServiceDeleteOverdue", lockAtMostFor = SCHEDULER_LOCK_INTERVAL)
//    fun deleteOverdueCandidates() {
//        LockAssert.assertLocked()
//        userRoomCandidateService.deleteAllOverdue()
//        todo: room_not_found - send to requester
//    }
