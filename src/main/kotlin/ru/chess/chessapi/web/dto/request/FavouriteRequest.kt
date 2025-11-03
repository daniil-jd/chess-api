package ru.chess.chessapi.web.dto.request

import java.util.UUID

data class FavouriteRequest(
    val room: UUID,
    val backendUserId: UUID,
    val favourite: Boolean
)
