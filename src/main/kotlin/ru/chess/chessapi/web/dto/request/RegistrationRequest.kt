package ru.chess.chessapi.web.dto.request

data class RegistrationRequest(
    val signature: String?,
    val name: String
)
