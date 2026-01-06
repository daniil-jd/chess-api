package ru.chess.chessapi.exception

import java.util.*

class ChangeFavouriteMatchException(userId: UUID, roomId: UUID) : RuntimeException(
    "Can't set/unset favourite to match - can't find GameHistoryEntity by userId: $userId and $roomId. Create GameHistory to this pair throw /history/save."
)