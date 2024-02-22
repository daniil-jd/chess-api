package ru.chess.chessapi.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.websocket.message.MatchFinishedMessageDto
import ru.chess.chessapi.websocket.message.MoveMessageDto
import ru.chess.chessapi.websocket.message.RequestMessageDto
import ru.chess.chessapi.websocket.message.enums.SideType
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.entity.UserRoomCandidateEntity
import ru.chess.chessapi.exception.RoomDoesNotExistException
import ru.chess.chessapi.exception.UserDoesNotExistException
import ru.chess.chessapi.exception.UsernameIsEmptyException
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
        with(requestMessageDto) {
            if (user == null) {
                throw UsernameIsEmptyException()
            }
            val userFromDb = userService.findUserByName(user) ?: throw UserDoesNotExistException(user)
            return userRoomCandidateService.createUserRoomCandidate(userFromDb, side)
        }
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

    private fun findRoomAndAddHistory(roomId: UUID, move: String): RoomEntity {
        val room = roomService.findRoomById(roomId) ?: throw RoomDoesNotExistException(roomId)
        val sb = if (room.history == null) {
            StringBuilder(move)
        } else {
            StringBuilder(room.history).also { it.append(" ").append(move) }
        }
        room.history = sb.toString()
        return room
    }

    @Transactional
    fun updateRoomHistory(roomId: UUID, move: String): RoomEntity {
        val room = findRoomAndAddHistory(roomId, move)
        return roomService.save(room)
    }

    @Transactional
    fun updateRoomHistoryWhenMatchIsOver(
        roomId: UUID, sideOfMove: SideType, finishType: String
    ): RoomEntity {
        val room = findRoomAndAddHistory(roomId, finishType).also {
            it.winnerSide = sideOfMove
            it.winType = finishType
        }
        return roomService.save(room)
    }

    private fun findAnotherUser(room: RoomEntity, sideOfMove: SideType): UserEntity {
        return if (sideOfMove == room.user1Side) {
            room.user2
        } else {
            room.user1
        }
    }

    @Transactional
    fun updateRoomHistoryAndReturnAnotherUser(roomId: UUID, sideOfMove: SideType, move: String): UserEntity {
        val room = updateRoomHistory(roomId, move)
        return findAnotherUser(room, sideOfMove)
    }

    @Transactional
    fun updateRoomHistoryAndReturnAnotherUserWhenMatchIsOver(
        roomId: UUID, winnerSide: SideType, finishType: String
    ): UserEntity {
        val room = updateRoomHistoryWhenMatchIsOver(roomId, winnerSide, finishType)
        return findAnotherUser(room, winnerSide)
    }

}
