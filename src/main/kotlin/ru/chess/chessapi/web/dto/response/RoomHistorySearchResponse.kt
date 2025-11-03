package ru.chess.chessapi.web.dto.response

import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.GameType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.time.LocalDateTime
import java.util.*

data class RoomHistorySearchResponse(
    val backendUserId: UUID,
    val signature: String?,
    val matchStatistics: List<MatchStatistic>,
    val matchesHistory: List<MatchHistory>
) {

    data class MatchStatistic(
        val gameType: GameType,
        val won: Int,
        val lost: Int,
        val draw: Int
    )

    data class MatchHistory(
        val roomId: UUID,
        val matchNumber: Long,
        val favourite: Boolean = false, // todo fix me
        val createdAt: String,
        val userSide: SideType,
        val userName: String,
        val opponentName: String,
        val opponentType: SideType,
        val gameType: GameType,
        val history: String,
        val winnerSide: SideType?,
        val finishType: FinishType
    )
}
