package ru.chess.chessapi.web.dto.response

import ru.chess.chessapi.web.websocket.message.enums.GameType
import java.util.UUID

data class RoomHistorySearchResponse(
    val backendUserId: UUID,
    val signature: String?,
    val points: Long,
    val matchStatistics: List<MatchStatistic>,
    val favouritesHistory: List<MatchHistoryResponse>,
    val matchesHistory: List<MatchHistoryResponse>
) {

    data class MatchStatistic(
        val gameType: GameType,
        val won: Long,
        val lost: Long,
        val draw: Long
    )
}
