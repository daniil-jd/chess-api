package ru.chess.chessapi.web.dto.response

import java.util.*

data class AuthResponse(
    val backendUserId: UUID,
    val playerName: String
)
