package ru.chess.chessapi.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import ru.chess.chessapi.exception.RoomDoesNotExistException
import ru.chess.chessapi.utils.findUserBySide
import ru.chess.chessapi.web.dto.request.RoomHistorySaveRequest
import ru.chess.chessapi.web.dto.response.RoomHistorySaveResponse
import ru.chess.chessapi.web.websocket.message.MatchFinishedMessageDto
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.SideType

@Service
class RoomHistorySaveService(
    private val roomService: RoomService,
    private val userService: UserService,
    private val gameHistoryService: GameHistoryService,
    private val wsSender: WsSender
) {

    private val log = KotlinLogging.logger {}

    @Transactional
    fun saveHistory(request: RoomHistorySaveRequest): RoomHistorySaveResponse {
        log.info { "request for save history: $request" }
        with(request) {
            val winnerSideFixed = if (!winnerSide.isNullOrBlank()) SideType.valueOf(winnerSide) else null
            log.info {
                "save history, roomId: ${request.room}, detected winnerSide: $winnerSideFixed, " +
                    "winnerSide from request: $winnerSide"
            }

            return if (room != null) {
                // room is not null -> room & user SHOULD exist already
                val roomEntity = roomService.findRoomByIdForUpdate(room) ?: throw RoomDoesNotExistException(room)
                val userEntity = roomEntity.findUserBySide(userSide)
                roomEntity.history = history
                // if ws socket is broken - need to save winner
                if (roomEntity.winType == null) {
                    log.info {
                        "save history, set winType ($finishType) & winnerSide ($winnerSideFixed) for roomId: $room"
                    }
                    roomEntity.winType = finishType
                    roomEntity.winnerSide = winnerSideFixed
                    val finished = MatchFinishedMessageDto(MessageType.MATCH_FINISHED_WS, room, winnerSideFixed, finishType)
                    TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                        override fun afterCommit() {
                            wsSender.sendMessageIfSessionExist(roomEntity.user1.id!!, finished)
                            wsSender.sendMessageIfSessionExist(roomEntity.user2.id!!, finished)
                        }
                    })
                }
                roomService.save(roomEntity)
                gameHistoryService.saveGameHistoryByRoom(userEntity, roomEntity, opponentType)

                RoomHistorySaveResponse(backendUserId = backendUserId!!, roomId = roomEntity.id!!)
            } else {
                // room is null -> room doesn't exist, this is user vs bot
                saveHistoryForUserAndBot(this, winnerSideFixed)
            }
        }
    }

    private fun saveHistoryForUserAndBot(
        request: RoomHistorySaveRequest,
        winnerSideFixed: SideType?
    ): RoomHistorySaveResponse {
        with(request) {
            val user = userService.searchOrCreateUserBySignatureAndId(
                signature = signature, backendUserId = backendUserId, username = username
            )
            return if (userSide == SideType.WHITE) {
                val blackBot = userService.findBotByColor(isWhite = false)

                val savedRoom = roomService.createRoomWithHistory(
                    user1 = user,
                    user1SideType = SideType.WHITE,
                    user1Name = username,
                    user2 = blackBot,
                    user2SideType = SideType.BLACK,
                    user2Name = opponentName,
                    gameType = opponentType,
                    history = history,
                    winnerSide = winnerSideFixed,
                    winType = finishType
                )
                gameHistoryService.saveGameHistoryByRoom(user, savedRoom, opponentType)
                RoomHistorySaveResponse(backendUserId = user.id!!, roomId = savedRoom.id!!)
            } else {
                val whiteBot = userService.findBotByColor(isWhite = true)

                val savedRoom = roomService.createRoomWithHistory(
                    user1 = whiteBot,
                    user1SideType = SideType.WHITE,
                    user1Name = opponentName,
                    user2 = user,
                    user2SideType = SideType.BLACK,
                    user2Name = username,
                    gameType = opponentType,
                    history = history,
                    winnerSide = winnerSideFixed,
                    winType = finishType
                )
                gameHistoryService.saveGameHistoryByRoom(user, savedRoom, opponentType)
                RoomHistorySaveResponse(backendUserId = user.id!!, roomId = savedRoom.id!!)
            }
        }
    }

}
