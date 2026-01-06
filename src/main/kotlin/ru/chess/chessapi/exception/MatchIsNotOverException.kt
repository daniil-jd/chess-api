package ru.chess.chessapi.exception

import java.util.*

class MatchIsNotOverException(roomId: UUID) : RuntimeException(
    "Match in room with id = $roomId is not over yet"
)
