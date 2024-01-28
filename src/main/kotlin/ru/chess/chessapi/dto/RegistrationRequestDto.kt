package ru.chess.chessapi.dto

import ru.chess.chessapi.dto.message.enums.SideType

data class RegistrationRequestDto(
    val username: String,
    val mail: String,
    val password: String,
    val userSide: SideType
)