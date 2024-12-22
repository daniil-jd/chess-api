package ru.chess.chessapi.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.entity.UserRoomCandidateEntity
import ru.chess.chessapi.exception.RoomDoesNotExistException
import ru.chess.chessapi.exception.UserDoesNotExistException
import ru.chess.chessapi.model.CandidatePair
import ru.chess.chessapi.web.dto.request.RoomHistorySaveRequest
import ru.chess.chessapi.web.dto.response.RoomHistorySaveResponse
import ru.chess.chessapi.web.dto.response.RoomHistorySearchResponse
import ru.chess.chessapi.web.websocket.message.RequestForRoomMessageDto
import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.PromotionType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.time.format.DateTimeFormatter
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
        val filteredSignature = signature?.let { it.ifBlank { null } }
        return when {
            // not authorized in yandex, first time
            filteredSignature == null && backendUserId == null -> {
                // create
                userService.createUser(username = username, signature = null)
            }

            // not authorized in yandex, not first time
            filteredSignature == null && backendUserId != null -> {
                // search or else create
                userService.findById(backendUserId) ?: userService.createUser(username = username, signature = null)
            }

            // authorized in yandex, first time
            filteredSignature != null && backendUserId == null -> {
                // create with signature
                userService.findBySignature(filteredSignature)
                    ?: userService.createUser(username = username, signature = filteredSignature)
            }

            // authorized in yandex, not first time
            // signature != null && backendUserId != null
            else -> {
                // search or else create
                userService.findBySignature(filteredSignature!!) ?: userService.findById(backendUserId!!)
                ?: userService.createUser(username = username, signature = filteredSignature)
            }
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
    fun updateRoomHistoryAndReturnAnotherUser(roomId: UUID, sideOfMove: SideType, move: String, promotionType: PromotionType?): UserEntity {
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
    fun saveHistory(request: RoomHistorySaveRequest): RoomHistorySaveResponse {
        with(request) {
            return if (room != null) {
                // room is not null -> room & user SHOULD exist already
                val room = roomService.findRoomById(room) ?: throw RoomDoesNotExistException(room)
                room.history = history
                roomService.save(room)

                RoomHistorySaveResponse(backendUserId = backendUserId!!)
            } else {
                // room is null -> room doesn't exist, this is user vs bot
                saveHistoryForUserAndBot(this)
            }
        }
    }

    fun getLatest20UserHistory(backendUserId: String?, signature: String?): RoomHistorySearchResponse {
        return when {
            // backendUserId is not null, not blank
            !backendUserId.isNullOrBlank() -> {
                val userId = try {
                    UUID.fromString(backendUserId)
                } catch (ex: Exception) {
                    logger.error { "Can't parse UUID from backendUserId: $backendUserId" }
                    throw UserDoesNotExistException(backendUserId)
                }
                val user = userService.findById(userId) ?: throw UserDoesNotExistException(backendUserId)
                val rooms = roomService.findLatest20RoomsByUser(user)
                val roomsCount = roomService.getCountByUser(user)

                RoomHistorySearchResponse(
                    backendUserId = userId,
                    signature = signature,
                    matchesHistory = rooms.mapIndexed { index, roomEntity ->
                        roomEntity.toMatchHistory(user, index, roomsCount)
                    }
                )
            }
            // only signature is present and not null, not blank
            backendUserId.isNullOrBlank() && !signature.isNullOrBlank() -> {
                val user = userService.findBySignature(signature) ?: throw UserDoesNotExistException(signature)
                val rooms = roomService.findLatest20RoomsByUser(user)
                val roomsCount = roomService.getCountByUser(user)

                RoomHistorySearchResponse(
                    backendUserId = user.id!!,
                    signature = signature,
                    matchesHistory = rooms.mapIndexed { index, roomEntity ->
                        roomEntity.toMatchHistory(user, index, roomsCount)
                    }
                )
            }
            // if backendUserId and signature are null or blank
            else -> {
                logger.error { "Can't find user and rooms by nullable backendUserid and signature" }
                throw UserDoesNotExistException("nothing")
            }
        }
    }

    private fun saveHistoryForUserAndBot(request: RoomHistorySaveRequest): RoomHistorySaveResponse {
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
            return RoomHistorySaveResponse(user.id!!)
        }
    }

    private fun RoomEntity.toMatchHistory(user: UserEntity, index: Int, count: Long): RoomHistorySearchResponse.MatchHistory {
        val createdAtWithoutSeconds = createdAt!!.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
        val matchNumber = count - index
        return when {
            this.user1 == user -> {
                // user1 = requested user, user2 = opponent
                RoomHistorySearchResponse.MatchHistory(
                    matchNumber = matchNumber,
                    createdAt = createdAtWithoutSeconds,
                    userSide = user1Side,
                    userName = user1.username,
                    opponentName = user2Name,
                    opponentType = user2Side,
                    gameType = gameType,
                    history = history!!,
                    winnerSide = winnerSide!!,
                    finishType = winType!!
                )
            }
            else -> {
                // user2 = requested user, user1 = opponent
                RoomHistorySearchResponse.MatchHistory(
                    matchNumber = matchNumber,
                    createdAt = createdAtWithoutSeconds,
                    userSide = user2Side,
                    userName = user2.username,
                    opponentName = user1Name,
                    opponentType = user1Side,
                    gameType = gameType,
                    history = history!!,
                    winnerSide = winnerSide!!,
                    finishType = winType!!
                )
            }
        }
    }
}
