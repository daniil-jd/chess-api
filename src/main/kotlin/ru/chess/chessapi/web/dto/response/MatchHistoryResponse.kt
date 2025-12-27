package ru.chess.chessapi.web.dto.response

import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.GameType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.util.UUID

data class MatchHistoryResponse(
    val roomId: UUID,
    val matchNumber: Long,
    val favourite: Boolean = false,
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
