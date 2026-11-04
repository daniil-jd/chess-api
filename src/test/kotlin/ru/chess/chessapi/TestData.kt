package ru.chess.chessapi

import ru.chess.chessapi.TestData.Users.DEFAULT_POINTS
import ru.chess.chessapi.TestData.Users.AUTH_CODE_1
import ru.chess.chessapi.TestData.Users.SIGNATURE_1
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.GameHistory
import ru.chess.chessapi.entity.GameHistoryId
import ru.chess.chessapi.entity.UserRoomCandidateEntity
import ru.chess.chessapi.utils.Constants.CANDIDATE_MAX_TIME_TO_LIVE
import ru.chess.chessapi.web.websocket.message.enums.GameType
import ru.chess.chessapi.web.websocket.message.enums.RoomStatus
import ru.chess.chessapi.web.websocket.message.enums.SideType
import ru.chess.chessapi.web.websocket.message.enums.UserGameStatus
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import ru.chess.chessapi.web.websocket.message.MoveMessageDto
import ru.chess.chessapi.web.websocket.message.MoveAcceptedMessage
import ru.chess.chessapi.web.websocket.message.RequestForRoomMessageDto
import ru.chess.chessapi.web.websocket.message.RequestForWsRetryDto
import ru.chess.chessapi.web.websocket.message.MatchFinishedMessageDto
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.PromotionType
import ru.chess.chessapi.web.websocket.message.enums.FinishType

object TestData {

    object Users {
        val USER_ID_1: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val USER_ID_2: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val USER_ID_3: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val USER_ID_4: UUID = UUID.fromString("00000000-0000-0000-0000-000000000004")
        val USER_ID_5: UUID = UUID.fromString("00000000-0000-0000-0000-000000000005")
        const val USERNAME_1 = "username1"
        const val USERNAME_2 = "username2"
        const val USERNAME_3 = "username3"
        const val USERNAME_4 = "username4"
        const val USERNAME_5 = "username5"
        const val SIGNATURE_1 = "signature1"
        const val SIGNATURE_2 = "signature2"
        const val AUTH_CODE_1 = "authCode1"
        const val AUTH_CODE_2 = "authCode2"
        const val BOT_USERNAME_1 = "bot1"
        const val BOT_USERNAME_2 = "bot2"
        const val WHITE_BOT_NAME = "white_bot"
        const val BLACK_BOT_NAME = "black_bot"
        const val SERIAL_NUMBER = 42L
        const val DEFAULT_POINTS = 0L
    }

    object AuthCodes {
        val UUIDS = listOf(
            UUID(0L, 0L),
            UUID(1L, 1L),
            UUID(Long.MAX_VALUE, Long.MAX_VALUE),
            UUID(Long.MIN_VALUE, Long.MIN_VALUE),
            UUID.fromString("12345678-1234-5678-9abc-def012345678"),
        )
        val SUM_CHAR_CASES = listOf(
            Triple("ABC", "BCD", "BDF"),
            Triple("XYZ", "BCD", "YAC"),
            Triple("abc", "bcd", "bdf"),
            Triple("AbC", "bCd", "BdF"),
            Triple("789", "456", "135"),
            Triple("XYZ", "123", "YAC"),
            Triple("xyz", "123", "yac"),
            Triple("789", "BCD", "802"),
            Triple("789", "bcd", "802"),
            Triple("#-!", "ABC", "#-!"),
            Triple("ABC", "#-!", "ABC"),
            Triple("A1#", "B2!", "B3#"),
            Triple("ABCD", "BC", "BDCD"),
            Triple("AB", "BCDE", "BDDE"),
            Triple("", "ABC", "ABC"),
            Triple("ABC", "", "ABC"),
            Triple("", "", ""),
        )
        val SQUID_CASES = listOf(
            "ABCDEFGH" to "ABC-DEF",
            "ABCDEFGHIJKLMNOPQRST" to "ABC-DEF",
            "A".repeat(20) + "B".repeat(20) to "BBBB-BB",
            "A".repeat(20) + "B".repeat(20) + "C".repeat(5) to "DDD-DDB",
            "K".repeat(20) to "K-KKKKK",
        )
    }

    object Sessions {
        const val SESSION_ID = "session1"
        const val SESSION_ID_2 = "session2"
        const val OLD_SESSION_ID = "oldSession"
    }

    object Rooms {
        const val HISTORY = "1. e4 e5 2. Nf3"
        val ROOM_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010")
        val ROOM_ID_2: UUID = UUID.fromString("00000000-0000-0000-0000-000000000011")
        val CURRENT_INSTANT: Instant = Instant.parse("2100-01-01T00:00:00Z")
        val CREATED_AT: Instant = CURRENT_INSTANT.minusSeconds(3600)
        val DEADLINE_AT: Instant = CURRENT_INSTANT.plusSeconds(3600)
    }

    object UserRoomCandidates {
        val CURRENT_TIME: LocalDateTime = LocalDateTime.parse("2000-01-01T00:00:00")
        val CREATED_AT: LocalDateTime = CURRENT_TIME.minusSeconds(CANDIDATE_MAX_TIME_TO_LIVE)
    }

    object GameHistories {
        const val MATCH_NUMBER = 1
        const val POINTS = 0
        val CREATED_AT: Instant = Rooms.CREATED_AT
    }

    object Models {
        object Request {
            fun roomHistorySaveRequest(
                room: UUID? = Rooms.ROOM_ID,
                userSide: SideType = SideType.WHITE,
                winnerSide: String? = SideType.WHITE.name,
                finishType: FinishType = FinishType.CHECKMATE,
                opponentType: GameType = GameType.ONLINE
            ) = ru.chess.chessapi.web.dto.request.RoomHistorySaveRequest(
                backendUserId = Users.USER_ID_1,
                signature = SIGNATURE_1,
                room = room,
                userSide = userSide,
                username = USERNAME_1,
                opponentName = Users.USERNAME_2,
                opponentType = opponentType,
                history = Rooms.HISTORY,
                winnerSide = winnerSide,
                finishType = finishType
            )
        }

        object Message {
            const val SERIALIZED_MESSAGE = "{\"messageType\":\"CHESS_MOVE_ACCEPTED\"}"
            const val SERIALIZED_ROOM_MESSAGE_1 = "{\"messageType\":\"ROOM_FOUND\",\"playerSide\":\"WHITE\"}"
            const val SERIALIZED_ROOM_MESSAGE_2 = "{\"messageType\":\"ROOM_FOUND\",\"playerSide\":\"BLACK\"}"
            const val SERIALIZED_SESSION_MESSAGE = "{\"messageType\":\"NEW_WS\"}"
            val MOVE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000020")
            const val MOVE = "e2e4"
            const val MOVE_FOR_HISTORY = "e4"
            const val PLY = 3

            fun moveMessage(
                promotionType: PromotionType? = null,
                sideOfMove: SideType = SideType.WHITE
            ) = MoveMessageDto(
                messageType = MessageType.CHESS_MOVE,
                backendUserId = Users.USER_ID_1,
                room = Rooms.ROOM_ID,
                sideOfMove = sideOfMove,
                move = MOVE,
                moveForHistory = MOVE_FOR_HISTORY,
                moveId = MOVE_ID,
                ply = PLY,
                promotionType = promotionType
            )

            fun moveAcceptedMessage() = MoveAcceptedMessage(
                room = Rooms.ROOM_ID,
                sideOfMove = SideType.WHITE,
                move = MOVE,
                moveId = MOVE_ID,
                ply = PLY,
                promotionType = null,
                turnServerTimeMs = Rooms.CURRENT_INSTANT.toEpochMilli()
            )

            fun requestForRoomMessage() = RequestForRoomMessageDto(
                messageType = MessageType.REQUEST_FOR_ROOM,
                signature = SIGNATURE_1,
                backendUserId = Users.USER_ID_1,
                username = USERNAME_1,
                side = SideType.WHITE
            )

            fun matchFinishedMessage(
                winnerSide: SideType = SideType.WHITE,
                finishType: FinishType = FinishType.CHECKMATE
            ) = MatchFinishedMessageDto(MessageType.MATCH_FINISHED, Rooms.ROOM_ID, winnerSide, finishType)

            fun requestForWsRetryMessage() = RequestForWsRetryDto(
                MessageType.WS_RETRY, Users.USER_ID_1, Sessions.OLD_SESSION_ID
            )
        }

        object Entity {
            fun gameHistory(
                user: UserEntity,
                room: RoomEntity,
                matchNumber: Int = GameHistories.MATCH_NUMBER,
                gameType: GameType = room.gameType,
                userGameStatus: UserGameStatus = UserGameStatus.WIN,
                favourite: Boolean = false,
                points: Int = GameHistories.POINTS,
                createdAt: Instant = GameHistories.CREATED_AT
            ) = GameHistory(
                id = GameHistoryId(userId = requireNotNull(user.id), roomId = requireNotNull(room.id)),
                user = user,
                room = room,
                matchNumber = matchNumber,
                gameType = gameType,
                userGameStatus = userGameStatus,
                favourite = favourite,
                points = points,
                createdAt = createdAt
            )

            fun userRoomCandidateEntity(
                user: UserEntity,
                userSide: SideType = SideType.WHITE,
                createdAt: LocalDateTime = UserRoomCandidates.CREATED_AT,
                activeUntil: LocalDateTime? = null
            ) = UserRoomCandidateEntity(
                user = user,
                userSide = userSide,
                createdAt = createdAt,
                activeUntil = activeUntil
            )

            fun roomEntity(
                user1: UserEntity = userEntity(id = Users.USER_ID_1),
                user2: UserEntity = userEntity(
                    id = Users.USER_ID_2,
                    username = Users.USERNAME_2,
                    signature = Users.SIGNATURE_2,
                    authCode = Users.AUTH_CODE_2
                ),
                gameType: GameType = GameType.ONLINE,
                roomStatus: RoomStatus = RoomStatus.ACTIVE,
                createdAt: Instant = Rooms.CREATED_AT,
                updatedAt: Instant? = Rooms.CREATED_AT,
                deadlineAt: Instant? = Rooms.DEADLINE_AT,
                id: UUID? = null,
                user1Side: SideType = SideType.WHITE,
                user2Side: SideType = SideType.BLACK,
                history: String? = null,
                winnerSide: SideType? = null,
                winType: FinishType? = null,
                lastMoveSide: SideType? = null
            ) = RoomEntity(
                id = id,
                user1 = user1,
                user1Side = user1Side,
                user1Name = user1.username,
                user2 = user2,
                user2Side = user2Side,
                user2Name = user2.username,
                gameType = gameType,
                history = history,
                winnerSide = winnerSide,
                winType = winType,
                lastMoveSide = lastMoveSide,
                roomStatus = roomStatus,
                createdAt = createdAt,
                updatedAt = updatedAt,
                deadlineAt = deadlineAt
            )

            fun userEntity(
                username: String = USERNAME_1,
                signature: String? = SIGNATURE_1,
                points: Long = DEFAULT_POINTS,
                authCode: String? = AUTH_CODE_1,
                isBot: Boolean = false,
                id: UUID? = null,
                serialNumber: Long? = null
            ) = UserEntity(
                id = id,
                serialNumber = serialNumber,
                username = username,
                signature = signature,
                totalPoints = points,
                authCode = authCode,
                isBot = isBot
            )
        }
    }

}
