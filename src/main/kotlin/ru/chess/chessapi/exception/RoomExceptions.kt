package ru.chess.chessapi.exception

import java.util.UUID

class RoomDoesNotExistException(roomId: UUID) : RuntimeException(
    "Room with id = $roomId doesn't exist"
)

class ActiveRoomDoesNotExistByUserIdException(userId: UUID) : RuntimeException(
    "Not finished room with user id = $userId doesn't exist"
)

class ActiveRoomDoesNotExistException(roomId: UUID) : RuntimeException(
    "Active room by room Id ($roomId) doesn't exist"
)

class ActiveRoomAlreadyExistException(userId1: UUID, userId2: UUID, roomId: UUID): RuntimeException(
    "Room already exist for user1 ($userId1), user2($userId2), room: $roomId"
)