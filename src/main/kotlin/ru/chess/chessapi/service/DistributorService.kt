package ru.chess.chessapi.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.entity.UserRoomCandidateEntity
import ru.chess.chessapi.exception.RoomDoesNotExistException
import ru.chess.chessapi.web.dto.request.RoomHistorySaveRequest
import ru.chess.chessapi.websocket.message.RequestForRoomMessageDto
import ru.chess.chessapi.websocket.message.enums.FinishType
import ru.chess.chessapi.websocket.message.enums.PromotionType
import ru.chess.chessapi.websocket.message.enums.SideType
import java.util.*

@Service
class DistributorService(
    private val userService: UserService,
    private val userRoomCandidateService: UserRoomCandidateService,
    private val roomService: RoomService
) {

    private val logger = KotlinLogging.logger {}

    @Transactional
    fun createUserRoomCandidate(requestMessageDto: RequestForRoomMessageDto): UserRoomCandidateEntity {
        with(requestMessageDto) {
            val user = searchOrCreateUserBySignatureAndId(
                signature = signature, backendUserId = backendUserId, username = username
            )

            return userRoomCandidateService.createUserRoomCandidate(user, side)
        }
    }

    private fun searchOrCreateUserBySignatureAndId(
        signature: String?,
        backendUserId: UUID?,
        username: String
    ): UserEntity {
        return when {
            // not authorized in yandex, first time
            signature == null && backendUserId == null -> {
                // create
                userService.createUser(username = username, signature = null)
            }

            // not authorized in yandex, not first time
            signature == null && backendUserId != null -> {
                // search or else create
                userService.findById(backendUserId) ?: userService.createUser(username = username, signature = null)
            }

            // authorized in yandex, first time
            signature != null && backendUserId == null -> {
                // create with signature
                userService.findBySignature(signature) ?:
                userService.createUser(username = username, signature = signature)
            }

            // authorized in yandex, not first time
            // signature != null && backendUserId != null
            else -> {
                // search or else create
                userService.findBySignature(signature!!) ?:
                userService.findById(backendUserId!!) ?:
                userService.createUser(username = username, signature = signature)
            }
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

    private fun findRoomAndAddHistory(roomId: UUID, move: String, promotionType: PromotionType? = null): RoomEntity {
        val room = roomService.findRoomById(roomId) ?: throw RoomDoesNotExistException(roomId)
        val sb = if (room.history == null) {
            StringBuilder(move)
        } else {
            StringBuilder(room.history).also { it.append(" ").append(move).append(promotionType?.toHistoryPart()) }
        }
        room.history = sb.toString()
        return room
    }

    @Transactional
    fun updateRoomHistory(roomId: UUID, move: String, promotionType: PromotionType?): RoomEntity {
        val room = findRoomAndAddHistory(roomId, move, promotionType)
        return roomService.save(room)
    }

    @Transactional
    fun updateRoomHistoryWhenMatchIsOver(
        roomId: UUID, sideOfMove: SideType, finishType: FinishType
    ): RoomEntity {
        val room = findRoomAndAddHistory(roomId, finishType.toString()).also {
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
    fun updateRoomHistoryAndReturnAnotherUser(roomId: UUID, sideOfMove: SideType, move: String, promotionType: PromotionType?): UserEntity {
        val room = updateRoomHistory(roomId, move, promotionType)
        return findAnotherUser(room, sideOfMove)
    }

    @Transactional
    fun updateRoomHistoryAndReturnAnotherUserWhenMatchIsOver(
        roomId: UUID, winnerSide: SideType, finishType: FinishType
    ): UserEntity {
        val room = updateRoomHistoryWhenMatchIsOver(roomId, winnerSide, finishType)
        return findAnotherUser(room, winnerSide)
    }

    @Transactional
    fun saveHistory(request: RoomHistorySaveRequest) {
        with(request) {
            val user = searchOrCreateUserBySignatureAndId(
                signature = signature, backendUserId = backendUserId, username = username
            )
            if (userSide == SideType.WHITE) {
                val blackBot = userService.findBotByColor(isWhite = false)

                roomService.createRoomWithHistory(
                    user1 = user,
                    user1SideType = SideType.WHITE,
                    user1Name = username,
                    user2 = blackBot,
                    user2SideType = SideType.BLACK,
                    user2Name = opponentName,
                    gameType = opponentType,
                    history = history,
                    winnerSide = winnerSide,
                    winType = finishType
                )
            } else {
                val whiteBot = userService.findBotByColor(isWhite = true)

                roomService.createRoomWithHistory(
                    user1 = whiteBot,
                    user1SideType = SideType.WHITE,
                    user1Name = opponentName,
                    user2 = user,
                    user2SideType = SideType.BLACK,
                    user2Name = username,
                    gameType = opponentType,
                    history = history,
                    winnerSide = winnerSide,
                    winType = finishType
                )
            }
        }
    }
}
