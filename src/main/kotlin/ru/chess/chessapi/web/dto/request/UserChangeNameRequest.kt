package ru.chess.chessapi.web.dto.request

import java.util.*

data class UserChangeNameRequest(
    val backendUserId: UUID,
    val newName: String
)
