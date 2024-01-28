package ru.chess.chessapi.dto

data class AuthenticationTokenRequestDto(
    val username: String,
    val password: String
)
