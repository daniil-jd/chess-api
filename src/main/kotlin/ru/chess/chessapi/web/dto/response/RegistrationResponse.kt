package ru.chess.chessapi.web.dto.response

data class RegistrationResponse(
    val userId: String,
    val number: Long,
    val name: String,
    val rating: Long
)
