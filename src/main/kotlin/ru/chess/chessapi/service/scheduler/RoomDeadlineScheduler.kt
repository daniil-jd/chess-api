package ru.chess.chessapi.service.scheduler

import net.javacrumbs.shedlock.core.LockAssert
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import ru.chess.chessapi.service.RoomService

@Service
@ConditionalOnProperty("scheduling.deadline.enabled", havingValue = "true")
class RoomDeadlineScheduler(
    private val roomService: RoomService
) {

    @Scheduled(fixedDelayString = "\${scheduling.deadline.fixed-delay-ms}")
    @SchedulerLock(name = "deadline-scheduler")
    fun start() {
        LockAssert.assertLocked()
        roomService.updateStatusIfDeadlineAtExpired()
    }
}