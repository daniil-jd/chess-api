package ru.chess.chessapi.exception

import java.util.UUID

class RoomDoesNotExistException(roomId: UUID) : RuntimeException(
    "Room with id = $roomId doesn't exist"
)