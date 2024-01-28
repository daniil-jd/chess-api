package ru.chess.chessapi.service.scheduler

import net.javacrumbs.shedlock.core.LockAssert
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.dto.message.enums.SideType
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.entity.UserRoomCandidateEntity
import ru.chess.chessapi.service.RoomService
import ru.chess.chessapi.service.SideService
import ru.chess.chessapi.service.UserRoomCandidateService
import ru.chess.chessapi.service.UserService
import ru.chess.chessapi.websocket.WSHandler
import java.io.Serializable
import java.util.*

@Service
class UserRoomCandidateSchedulerService(
    private val userRoomCandidateService: UserRoomCandidateService,
    private val userService: UserService,
    private val roomService: RoomService,
    private val sideService: SideService,
    private val wsHandler: WSHandler
) {
    companion object {
        const val SCHEDULER_LOCK_INTERVAL = "5s"
    }

    @Transactional
    @Scheduled(
        fixedRateString = "PT15S",
        initialDelayString =  "PT10S"
    )
    @SchedulerLock(name = "UserRoomCandidateSchedulerService", lockAtMostFor = SCHEDULER_LOCK_INTERVAL)
    fun searchCandidates() {
        LockAssert.assertLocked()
        val candidates = userRoomCandidateService.findActualUserRoomCandidates()
        if (candidates.size >= 2) {
            val pairsToCreate = mutableSetOf<CandidatePair<UserRoomCandidateEntity, UserRoomCandidateEntity>>()
            val temp = mutableSetOf<UserRoomCandidateEntity>()
            for (i in 0..candidates.size - 2) {
                val canA = candidates[i].user
                for (j in i + 1..candidates.size - 1) {
                    val canB = candidates[j].user
                    if ((canB.userSide == sideService.findOppositeSide(canA.userSide) ||
                            canB.userSide == SideType.RANDOM && canA.userSide == SideType.RANDOM) &&
                        !(temp.contains(candidates[j]) || temp.contains(candidates[i]))) {
                        pairsToCreate.add(CandidatePair(candidates[j], candidates[i]))
                        temp.add(candidates[j])
                        temp.add(candidates[i])
                        break
                    }
                }
            }
            pairsToCreate.forEach {
                val first = it.first.user
                val second = it.second.user
                changeSidesIfItNeeds(user1 = first, user2 = second)
                val room = roomService.createRoom(first, second)

                userRoomCandidateService.deleteCandidates(listOf(it.first, it.second))
                wsHandler.sendRoomCreatedMessage(room)
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

    private fun findCandidateWithNeededSide(
        candidates: MutableList<UserRoomCandidateEntity>,
        currentUserSide: SideType
    ): Optional<UserRoomCandidateEntity> {
        val result = when (currentUserSide) {
            SideType.WHITE -> {
                candidates.find { c -> c.user.userSide == SideType.BLACK || c.user.userSide == SideType.RANDOM }
            }

            SideType.BLACK -> {
                candidates.find { c -> c.user.userSide == SideType.WHITE || c.user.userSide == SideType.RANDOM }
            }

            SideType.RANDOM -> {
                candidates.find { c ->
                    c.user.userSide == SideType.BLACK ||
                        c.user.userSide == SideType.WHITE ||
                        c.user.userSide == SideType.RANDOM
                }
            }
        }
        return if (result != null) {
            Optional.of(result)
        } else {
            Optional.empty()
        }
    }

    private fun changeSidesIfItNeeds(user1: UserEntity, user2: UserEntity) {
        if (user1.userSide == SideType.RANDOM) {
            if (user2.userSide == SideType.RANDOM) {
                userService.replaceRandomSide(user1, SideType.WHITE)
                userService.replaceRandomSide(user2, SideType.BLACK)
            }
            userService.replaceRandomSide(user1, sideService.findOppositeSide(user2.userSide))
        }
        if (user2.userSide == SideType.RANDOM) {
            userService.replaceRandomSide(user2, sideService.findOppositeSide(user1.userSide))
        }
    }
}

data class CandidatePair<out A, out B>(
    val first: A,
    val second: B
) : Serializable {
    override fun toString(): String = "($first, $second)"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CandidatePair<*, *>

        if (first != other.first) return false
        if (second != other.second) return false

        return first == other.first && second == other.second ||
            first == other.second && second == other.first
    }

    override fun hashCode(): Int {
        return Objects.hashCode(first) + Objects.hashCode(second)
    }
}
