package ru.chess.chessapi.service.scheduler

import mu.KotlinLogging
import net.javacrumbs.shedlock.core.LockAssert
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.websocket.message.enums.SideType
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.entity.UserRoomCandidateEntity
import ru.chess.chessapi.service.RoomService
import ru.chess.chessapi.service.SideService
import ru.chess.chessapi.service.UserRoomCandidateService
import ru.chess.chessapi.websocket.WSHandler
import java.io.Serializable
import java.util.*

@Service
class UserRoomCandidateSchedulerService(
    private val userRoomCandidateService: UserRoomCandidateService,
    private val roomService: RoomService,
    private val sideService: SideService,
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
        val candidates = userRoomCandidateService.findActiveUserRoomCandidates()
        if (candidates.size >= 2) {
            val pairsToCreate = mutableSetOf<CandidatePair<UserRoomCandidateEntity, UserRoomCandidateEntity>>()
            val temp = mutableSetOf<UserRoomCandidateEntity>()
            for (i in 0..candidates.size - 2) {
                val sideA = candidates[i].userSide
                for (j in i + 1 until candidates.size) {
                    val sideB = candidates[j].userSide
                    if ((sideB == sideService.findOppositeSide(sideA) ||
                            sideB == SideType.RANDOM && sideA == SideType.RANDOM) &&
                        !(temp.contains(candidates[j]) || temp.contains(candidates[i]))
                    ) {
                        pairsToCreate.add(CandidatePair(candidates[j], candidates[i]))
                        temp.add(candidates[j])
                        temp.add(candidates[i])
                        break
                    }
                }
            }
            pairsToCreate.forEach {
                val first = it.first.user
                val firstSide = it.first.userSide
                val second = it.second.user
                val secondSide = it.second.userSide
                val room = createRoomWithCandidates(first, firstSide, second, secondSide)

                userRoomCandidateService.deleteCandidates(listOf(it.first, it.second))
                wsHandler.sendRoomCreatedMessage(room)
            }
        }
    }

    private fun createRoomWithCandidates(
        user1: UserEntity, side1: SideType, user2: UserEntity, side2: SideType
    ): RoomEntity {
        return when (side1) {
            SideType.RANDOM -> {
                when (side2) {
                    SideType.RANDOM, SideType.BLACK -> {
                        roomService.createRoomForOnlineMatch(
                            user1 = user1,
                            user1SideType = SideType.WHITE,
                            user1Name = user1.username,
                            user2 = user2,
                            user2SideType = SideType.BLACK,
                            user2Name = user2.username
                        )
                    }
                    SideType.WHITE -> {
                        roomService.createRoomForOnlineMatch(
                            user1 = user2,
                            user1SideType = SideType.BLACK,
                            user1Name = user2.username,
                            user2 = user1,
                            user2SideType = SideType.WHITE,
                            user2Name = user1.username
                        )
                    }
                }
            }
            SideType.WHITE -> {
                when (side2) {
                    SideType.RANDOM, SideType.BLACK -> {
                        roomService.createRoomForOnlineMatch(
                            user1 = user1,
                            user1SideType = SideType.WHITE,
                            user1Name = user1.username,
                            user2 = user2,
                            user2SideType = SideType.BLACK,
                            user2Name = user2.username
                        )
                    }
                    else -> {
                        logger.error {
                            "User 1 is white and user 2 is white, something go wrong. User1: $user1, user2: $user2"
                        }
                        throw Exception(
                            "User 1 is white and user 2 is white, something go wrong. User1: $user1, user2: $user2"
                        )
                    }
                }
            }
            SideType.BLACK -> {
                when (side2) {
                    SideType.RANDOM, SideType.WHITE -> {
                        roomService.createRoomForOnlineMatch(
                            user1 = user1,
                            user1SideType = SideType.BLACK,
                            user1Name = user1.username,
                            user2 = user2,
                            user2SideType = SideType.WHITE,
                            user2Name = user2.username
                        )
                    }
                    else -> {
                        logger.error {
                            "User 1 is black and user 2 is black, something go wrong. User1: $user1, user2: $user2"
                        }
                        throw Exception(
                            "User 1 is black and user 2 is black, something go wrong. User1: $user1, user2: $user2"
                        )
                    }
                }
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
