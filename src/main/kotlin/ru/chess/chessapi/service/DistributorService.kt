package ru.chess.chessapi.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.dto.message.MatchFinishedMessageDto
import ru.chess.chessapi.dto.message.MoveMessageDto
import ru.chess.chessapi.dto.message.RequestMessageDto
import ru.chess.chessapi.dto.message.enums.SideType
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.entity.UserRoomCandidateEntity
import java.util.*

@Service
class DistributorService(
    private val userService: UserService,
    private val userRoomCandidateService: UserRoomCandidateService,
    private val roomService: RoomService,
    private val sideService: SideService
) {

    private val logger = KotlinLogging.logger {}

    @Transactional
    fun createUserRoomCandidate(requestMessageDto: RequestMessageDto): UserRoomCandidateEntity {
        val user = userService.findOrCreate(requestMessageDto.user, requestMessageDto.side)
        return userRoomCandidateService.createUserRoomCandidate(user)
    }

    @Transactional
    fun cancelRoomCandidate(userId: UUID) {
        val user = userService.findById(userId)
        if (user != null) {
            userRoomCandidateService.deleteByUser(user)
            logger.info { "UserRoomCandidate deleted by userId: $userId" }
        } else {
            logger.warn { "Nothing found by userId: $userId" }
        }
    }

    fun findAnotherUserInGivenRoom(move: MoveMessageDto): UserEntity {
        return findAnotherUser(move.room, move.sideOfMove)
    }

    fun findAnotherUserInGivenRoomWhenMatchIsOver(matchFinishedDto: MatchFinishedMessageDto): UserEntity {
        val sideOfMove = sideService.findOppositeSide(matchFinishedDto.winnerSide)
        return findAnotherUser(matchFinishedDto.room, sideOfMove)
    }

    fun findAnotherUser(roomId: UUID, sideOfMove: SideType): UserEntity {
        val room = roomService.findRoomById(roomId)
        if (room.isPresent) {
            if (sideOfMove == room.get().user1.userSide && room.get().user2 != null) {
                return room.get().user2!!
            } else if (room.get().user2 != null &&
                sideOfMove == room.get().user2!!.userSide) {
                return room.get().user1
            }
        }
        throw Exception("Can't find room: $roomId, or another user in room with given side of move: $sideOfMove")
    }

}
