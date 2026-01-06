package ru.chess.chessapi.web.dto.security

data class AuthenticationTokenRequestDto(
    val username: String,
    val password: String
)
