package ru.chess.chessapi.dto

import ru.chess.chessapi.entity.UserEntity

data class UserDto(
    val id: String,
    val username: String,
    val mail: String?,
    val isEnabled: Boolean
)

fun UserEntity.toDto(): UserDto = UserDto(
    id = this.id.toString(),
    username = this.username,
    mail = this.mail,
    isEnabled = this.isEnabled
)
