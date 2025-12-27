package ru.chess.chessapi.web.dto.response

import ru.chess.chessapi.web.websocket.message.enums.GameType
import java.util.UUID

data class RoomHistorySearchResponse(
    val backendUserId: UUID,
    val signature: String?,
    val matchStatistics: List<MatchStatistic>,
    val favouritesHistory: List<MatchHistoryResponse>,
    val matchesHistory: List<MatchHistoryResponse>
) {

    data class MatchStatistic(
        val gameType: GameType,
        val won: Int,
        val lost: Int,
        val draw: Int
    )
}
