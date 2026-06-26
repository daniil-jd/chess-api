package ru.chess.chessapi.utils

import ru.chess.chessapi.entity.GameHistory
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.exception.UserNotInRoomException
import ru.chess.chessapi.model.GameStatsProjection
import ru.chess.chessapi.web.dto.response.MatchHistoryResponse
import ru.chess.chessapi.web.dto.response.RoomHistorySearchResponse
import ru.chess.chessapi.web.websocket.message.enums.GameType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import ru.chess.chessapi.web.websocket.message.enums.UserGameStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun isUserWinner(user: UserEntity, room: RoomEntity): UserGameStatus {
    val userSide = if (room.user1.id == user.id) {
        room.user1Side
    } else if (room.user2.id == user.id) {
        room.user2Side
    } else {
        throw UserNotInRoomException(user.id!!, room.id!!)
    }

    return when {
        // draw
        room.winnerSide == null -> {
            UserGameStatus.DRAW
        }
        // win
        room.winnerSide != null && userSide == room.winnerSide!! -> {
            UserGameStatus.WIN
        }
        // lose
        else -> {
            UserGameStatus.LOSE
        }
    }
}

fun GameHistory.toMatchHistory(): MatchHistoryResponse {
    val createdAtWithoutSeconds = createdAt!!
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))

    return when {
        room.user1 == user -> {
            // user1 = requested user, user2 = opponent
            MatchHistoryResponse(
                roomId = room.id!!,
                matchNumber = matchNumber,
                createdAt = createdAtWithoutSeconds,
                favourite = favourite,
                userSide = room.user1Side,
                userName = room.user1.username,
                opponentName = room.user2Name,
                opponentType = room.user2Side,
                gameType = room.gameType,
                history = room.history!!,
                winnerSide = room.winnerSide,
                finishType = room.winType!!
            )
        }
        else -> {
            // user2 = requested user, user1 = opponent
            MatchHistoryResponse(
                roomId = room.id!!,
                matchNumber = matchNumber,
                createdAt = createdAtWithoutSeconds,
                favourite = favourite,
                userSide = room.user2Side,
                userName = room.user2.username,
                opponentName = room.user1Name,
                opponentType = room.user1Side,
                gameType = room.gameType,
                history = room.history!!,
                winnerSide = room.winnerSide,
                finishType = room.winType!!
            )
        }
    }
}

fun RoomEntity.findUserBySide(userSideType: SideType): UserEntity {
    return if (user1Side == userSideType) user1 else user2
}

fun RoomEntity.findAnotherUser(user: UserEntity): UserEntity {
    return if (user1.id == user.id!!)
        user2
    else
        user1
}

fun GameStatsProjection.toDto() : RoomHistorySearchResponse.MatchStatistic {
    return RoomHistorySearchResponse.MatchStatistic(
        gameType = GameType.valueOf(gameType),
        won = wins,
        lost = losses,
        draw = draws
    )
}