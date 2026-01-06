package ru.chess.chessapi.web.dto.request

import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.GameType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.util.*

data class RoomHistorySaveRequest(
    val backendUserId: UUID?,
    val signature: String?,
    val room: UUID?,
    val userSide: SideType,
    val username: String,
    val opponentName: String,
    val opponentType: GameType,
    val history: String,
    // can be "", null or valid value
    val winnerSide: String? = null,
    val finishType: FinishType
)
