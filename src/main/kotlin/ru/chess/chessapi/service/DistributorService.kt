package ru.chess.chessapi.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.entity.UserRoomCandidateEntity
import ru.chess.chessapi.exception.RoomDoesNotExistByUserIdException
import ru.chess.chessapi.exception.RoomDoesNotExistException
import ru.chess.chessapi.exception.UserDoesNotExistException
import ru.chess.chessapi.model.CandidatePair
import ru.chess.chessapi.web.dto.request.AuthBySignatureRequest
import ru.chess.chessapi.web.dto.request.UserChangeNameRequest
import ru.chess.chessapi.web.dto.response.AuthCodeResponse
import ru.chess.chessapi.web.dto.response.AuthResponse
import ru.chess.chessapi.web.websocket.message.RequestForRoomMessageDto
import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.PromotionType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.util.*

@Service
class DistributorService(
    private val userService: UserService,
    private val userRoomCandidateService: UserRoomCandidateService,
    private val roomService: RoomService,
    private val authCodeService: AuthCodeService
) {

    private val logger = KotlinLogging.logger {}

    @Transactional
    fun createUserRoomCandidate(requestMessageDto: RequestForRoomMessageDto): UserRoomCandidateEntity {
        with(requestMessageDto) {
            val user = userService.searchOrCreateUserBySignatureAndId(
                signature = signature, backendUserId = backendUserId, username = username
            )

            return userRoomCandidateService.createUserRoomCandidate(user, side)
        }
    }



    @Transactional
    fun searchCandidatesAndCreateRooms(): List<RoomEntity> {
        val candidates = userRoomCandidateService.findActiveUserRoomCandidates()
        if (candidates.size >= 2) {
            val pairsToCreate = mutableSetOf<CandidatePair<UserRoomCandidateEntity, UserRoomCandidateEntity>>()
            val temp = mutableSetOf<UserRoomCandidateEntity>()
            for (i in 0..candidates.size - 2) {
                val sideA = candidates[i].userSide
                for (j in i + 1 until candidates.size) {
                    val sideB = candidates[j].userSide
                    if ((sideB == findOppositeSide(sideA) || sideB == SideType.RANDOM) &&
                        !(temp.contains(candidates[j]) || temp.contains(candidates[i]))
                    ) {
                        pairsToCreate.add(CandidatePair(candidates[j], candidates[i]))
                        temp.add(candidates[j])
                        temp.add(candidates[i])
                        break
                    }
                }
            }
            return pairsToCreate.map {
                val first = it.first.user
                val firstSide = it.first.userSide
                val second = it.second.user
                val secondSide = it.second.userSide
                val room = createRoomWithCandidates(first, firstSide, second, secondSide)
                userRoomCandidateService.deleteCandidates(listOf(it.first, it.second))
                room
            }
        }
        return emptyList()
    }

    private fun findOppositeSide(side: SideType): SideType {
        return when (side) {
            SideType.WHITE -> {
                SideType.BLACK
            }

            SideType.BLACK -> {
                SideType.WHITE
            }

            SideType.RANDOM -> {
                SideType.WHITE
            }
        }
    }

    @Transactional
    fun createRoomWithCandidates(
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
                            user1SideType = SideType.WHITE,
                            user1Name = user2.username,
                            user2 = user1,
                            user2SideType = SideType.BLACK,
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
            StringBuilder(room.history).also {
                it.append(" ").append(move).append(promotionType?.toHistoryPart() ?: "")
            } // todo fix null to history
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
    fun updateRoomHistoryAndReturnAnotherUser(
        roomId: UUID,
        sideOfMove: SideType,
        move: String,
        promotionType: PromotionType?
    ): UserEntity {
        val room = updateRoomHistory(roomId, move, promotionType)
        return findAnotherUser(room, sideOfMove)
    }

    @Transactional
    fun updateRoomHistoryAndReturnAnotherUserWhenMatchIsOver(
        roomId: UUID, winnerSide: SideType, finishType: FinishType
    ): UserEntity {
        val room = updateRoomHistoryWhenMatchIsOver(roomId, winnerSide, finishType)
        return findAnotherUser(room, findOppositeSide(winnerSide))
    }

    @Transactional
    fun authBySignature(request: AuthBySignatureRequest): AuthResponse {
        val user = userService.searchOrCreateUserBySignatureAndId(
            signature = request.signature,
            backendUserId = null,
            username = request.playerName
        )

        return with(user) {
            AuthResponse(
                backendUserId = id!!,
                playerName = username
            )
        }
    }

    @Transactional
    fun generateAuthCode(backendUserId: UUID): AuthCodeResponse {
        val user = userService.findById(backendUserId) ?: throw UserDoesNotExistException(backendUserId.toString())

        val authCode = authCodeService.generateShortAuthCode(backendUserId)

        user.authCode = authCode
        userService.save(user)
        return AuthCodeResponse(authCode)
    }

    fun validateAuthCode(authCode: String): AuthResponse {
        val user = userService.findByAuthCode(authCode) ?: throw UserDoesNotExistException(authCode)

        return AuthResponse(user.id!!, user.username)
    }

    @Transactional
    fun changeUserName(request: UserChangeNameRequest) {
        with(request) {
            val user = userService.findById(backendUserId) ?: throw UserDoesNotExistException(backendUserId.toString())
            user.username = newName
            userService.save(user)
        }
    }

    fun findNotFinishedRoomByUserId(backendUserId: UUID): RoomEntity {
        val user = userService.findById(backendUserId) ?: throw UserDoesNotExistException(backendUserId.toString())
        return roomService.findByUser(user) ?: throw RoomDoesNotExistByUserIdException(backendUserId)
    }
}
