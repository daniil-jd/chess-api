package ru.chess.chessapi.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import ru.chess.chessapi.entity.GameHistory
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.exception.MatchIsNotOverException
import ru.chess.chessapi.exception.RoomDoesNotExistException
import ru.chess.chessapi.exception.UserDoesNotExistException
import ru.chess.chessapi.utils.toMatchHistory
import ru.chess.chessapi.web.dto.response.HistoryRoomResponse
import ru.chess.chessapi.web.dto.response.RoomHistorySearchResponse
import ru.chess.chessapi.web.websocket.message.enums.GameType
import ru.chess.chessapi.web.websocket.message.enums.UserGameStatus
import java.util.*

@Service
class RoomHistorySearchService(
    private val roomService: RoomService,
    private val userService: UserService,
    private val gameHistoryService: GameHistoryService
) {

    private val logger = KotlinLogging.logger {}

    fun getLatest30GamesEachType(backendUserId: String?, signature: String?): RoomHistorySearchResponse {
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

                prepareRoomHistoryResponse(user)
            }
            // only signature is present and not null, not blank
            backendUserId.isNullOrBlank() && !signature.isNullOrBlank() -> {
                val user = userService.findBySignature(signature) ?: throw UserDoesNotExistException(signature)

                prepareRoomHistoryResponse(user)
            }
            // if backendUserId and signature are null or blank
            else -> {
                logger.error { "Can't find user and rooms by nullable backendUserid and signature" }
                throw UserDoesNotExistException("nothing")
            }
        }
    }

    fun getHistoryRoomById(roomId: UUID): HistoryRoomResponse {
        val room = roomService.findRoomById(roomId) ?: throw RoomDoesNotExistException(roomId)
        if (room.winType == null) throw MatchIsNotOverException(roomId)
        return with(room) {
            HistoryRoomResponse(
                room = id!!,
                playerWhiteName = user1Name,
                playerBlackName = user2Name,
                history = history!!,
                finishType = winType!!,
                winnerSide = winnerSide!!
            )
        }
    }

    private fun prepareRoomHistoryResponse(user: UserEntity): RoomHistorySearchResponse {
        val games = mutableListOf<GameHistory>().apply {
            addAll(gameHistoryService.findLast30ByUserAndGameType(user, GameType.ONLINE))
            addAll(gameHistoryService.findLast30ByUserAndGameType(user, GameType.BOT))
            addAll(gameHistoryService.findLast30ByUserAndGameType(user, GameType.PSEUDO))
            addAll(gameHistoryService.findLast30ByUserAndGameType(user, GameType.LOCAL))
        }

        val matchStatistic = games
            .groupBy { game -> game.gameType }
            .mapValues { (key, values) ->
                var won = 0
                var lost = 0
                var draw = 0

                values.forEach { game ->
                    when (game.userGameStatus) {
                        UserGameStatus.LOSE -> lost++
                        UserGameStatus.DRAW -> draw++
                        UserGameStatus.WIN -> won++
                    }
                }

                RoomHistorySearchResponse.MatchStatistic(key, won = won, lost = lost, draw = draw)
            }.values.toList()

        val allFavouritesByUser = gameHistoryService.findAllByUserAndFavourite(user, true)

        return RoomHistorySearchResponse(
            backendUserId = user.id!!,
            signature = user.signature,
            matchStatistics = matchStatistic,
            favouritesHistory = allFavouritesByUser.map { it.toMatchHistory() },
            matchesHistory = games.map { it.toMatchHistory() }
        )
    }
}
