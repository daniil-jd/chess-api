package ru.chess.chessapi.web.dto.response

import ru.chess.chessapi.websocket.message.enums.FinishType
import ru.chess.chessapi.websocket.message.enums.GameType
import ru.chess.chessapi.websocket.message.enums.SideType
import java.time.LocalDateTime
import java.util.*

data class RoomHistorySearchResponse(
    val backendUserId: UUID,
    val signature: String?,
    val matchesHistory: List<MatchHistory>
) {

    data class MatchHistory(
        val matchNumber: Long,
        // todo "yyyy.mm.dd HH:MM"
        val createdAt: LocalDateTime,
        val userSide: SideType,
        val userName: String,
        val opponentName: String,
        val opponentType: SideType,
        val gameType: GameType,
        val history: String,
        val winnerSide: SideType,
        val finishType: FinishType
    )
}
