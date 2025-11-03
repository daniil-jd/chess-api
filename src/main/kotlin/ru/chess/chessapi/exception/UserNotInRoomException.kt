package ru.chess.chessapi.exception

import java.util.*

class UserNotInRoomException(userId: UUID, roomId: UUID) : RuntimeException(
    "User $userId not in room $roomId, but expected"
)