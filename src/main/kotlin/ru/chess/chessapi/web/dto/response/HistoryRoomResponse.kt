package ru.chess.chessapi.web.dto.response

import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.util.UUID

data class HistoryRoomResponse(
    val room: UUID,
    val playerWhiteName: String,
    val playerBlackName: String,
    val history: String,
    val finishType: FinishType,
    val winnerSide: SideType
)
