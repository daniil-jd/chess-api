package ru.chess.chessapi.web.dto.request

import ru.chess.chessapi.websocket.message.enums.FinishType
import ru.chess.chessapi.websocket.message.enums.GameType
import ru.chess.chessapi.websocket.message.enums.SideType
import java.util.*

data class RoomHistorySaveRequest(
    val backendUserId: UUID?,
    val signature: String?,
    val userSide: SideType,
    val username: String,
    val opponentName: String,
    val opponentType: GameType,
    val history: String,
    val winnerSide: SideType,
    val finishType: FinishType
)
