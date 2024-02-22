package ru.chess.chessapi.web.dto.security

import ru.chess.chessapi.websocket.message.enums.SideType

data class RegistrationRequestDto(
    val username: String,
    val mail: String,
    val password: String,
    val userSide: SideType
)