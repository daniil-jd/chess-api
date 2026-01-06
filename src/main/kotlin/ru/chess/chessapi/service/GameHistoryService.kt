package ru.chess.chessapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.entity.GameHistory
import ru.chess.chessapi.entity.GameHistoryId
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.exception.ChangeFavouriteMatchException
import ru.chess.chessapi.exception.RoomDoesNotExistException
import ru.chess.chessapi.exception.UserNotInRoomException
import ru.chess.chessapi.repository.GameHistoryRepository
import ru.chess.chessapi.utils.isUserWinner
import ru.chess.chessapi.web.dto.request.FavouriteRequest
import ru.chess.chessapi.web.dto.response.FavouriteRoomResponse
import ru.chess.chessapi.web.websocket.message.enums.GameType
import ru.chess.chessapi.web.websocket.message.enums.UserGameStatus
import java.time.ZoneOffset

@Service
class GameHistoryService(
    private val gameHistoryRepository: GameHistoryRepository,
    private val roomService: RoomService
) {

    @Transactional
    fun saveGameHistoryByRoom(room: RoomEntity): List<GameHistory> {
        return with(room) {
            when (isUserWinner(room.user1, room)) {
                UserGameStatus.LOSE -> {
                    saveAll(
                        listOf(
                            prepareGameHistory(user1, this, UserGameStatus.LOSE),
                            prepareGameHistory(user2, this, UserGameStatus.WIN)
                        )
                    )
                }
                UserGameStatus.DRAW -> {
                    saveAll(
                        listOf(
                            prepareGameHistory(user1, this, UserGameStatus.DRAW),
                            prepareGameHistory(user2, this, UserGameStatus.DRAW)
                        )
                    )
                }
                UserGameStatus.WIN -> {
                    saveAll(
                        listOf(
                            prepareGameHistory(user1, this, UserGameStatus.WIN),
                            prepareGameHistory(user2, this, UserGameStatus.LOSE)
                        )
                    )
                }
            }
        }
    }

    @Transactional
    fun markOrUnmarkFavourite(request: FavouriteRequest): FavouriteRoomResponse {
        with(request) {
            val roomEntity = roomService.findRoomById(room) ?: throw RoomDoesNotExistException(room)

            val userEntity = when (backendUserId) {
                roomEntity.user1.id!! -> {
                    roomEntity.user1
                }
                roomEntity.user2.id!! -> {
                    roomEntity.user2
                }
                else -> {
                    throw UserNotInRoomException(userId = backendUserId, roomId = room)
                }
            }

            val gameHistory = findOrCreateIfNotExists(userEntity, roomEntity)
            return when {
                gameHistory.favourite -> {
                    gameHistory.favourite = false
                    gameHistoryRepository.save(gameHistory)
                    FavouriteRoomResponse(
                        status = FavouriteRoomResponse.Status.DELETED
                    )
                }
                !gameHistory.favourite -> {
                    gameHistory.favourite = true
                    gameHistoryRepository.save(gameHistory)
                    FavouriteRoomResponse(
                        status = FavouriteRoomResponse.Status.CREATED
                    )
                }
                else -> {
                    throw ChangeFavouriteMatchException(userId = userEntity.id!!, roomId = roomEntity.id!!)
                }
            }
        }
    }

    @Transactional
    fun findOrCreateIfNotExists(userEntity: UserEntity, roomEntity: RoomEntity): GameHistory {
        val gameHistory = gameHistoryRepository.findByUserAndRoom(userEntity, roomEntity)
        if (gameHistory != null) return gameHistory

        val gamesHistory = saveGameHistoryByRoom(roomEntity).associateBy { it.user.id!! }
        return gamesHistory[userEntity.id!!]!!
    }

    fun findLast30ByUserAndGameType(user: UserEntity, gameType: GameType): List<GameHistory> {
        return gameHistoryRepository.findLatest30RoomsByUserAndGameType(user.id!!, gameType.toString())
    }

    fun findAllByUserAndFavourite(user: UserEntity, favourite: Boolean): List<GameHistory> {
        return gameHistoryRepository.findAllByUserAndFavourite(user, favourite)
    }

    fun save(gameHistory: GameHistory) {
        gameHistoryRepository.save(gameHistory)
    }

    fun saveAll(gameHistoryList: List<GameHistory>): List<GameHistory> {
        return gameHistoryRepository.saveAllAndFlush(gameHistoryList)
    }

    fun calculateNextMatchNumber(user: UserEntity): Int {
        val currentMax = gameHistoryRepository.maxMatchNumberForUser(user.id!!)
        return currentMax + 1
    }

    private fun prepareGameHistory(
        user: UserEntity,
        room: RoomEntity,
        userGameStatus: UserGameStatus
    ): GameHistory {
        val gameType = room.gameType
        val matchNumber = calculateNextMatchNumber(user)
        return GameHistory(
            id = prepareGameHistoryId(user, room),
            user = user,
            room = room,
            matchNumber = matchNumber,
            gameType = gameType,
            userGameStatus = userGameStatus,
            favourite = false,
            createdAt = room.createdAt!!.toInstant(ZoneOffset.UTC)
        )
    }

    private fun prepareGameHistoryId(user: UserEntity, room: RoomEntity) = GameHistoryId(
        userId = user.id!!,
        roomId = room.id!!
    )
}
