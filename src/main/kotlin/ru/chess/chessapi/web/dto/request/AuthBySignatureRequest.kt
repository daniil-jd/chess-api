package ru.chess.chessapi.web.dto.request

data class AuthBySignatureRequest(
    val signature: String?,
    val playerName: String
)
