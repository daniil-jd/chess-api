package ru.chess.chessapi.dto

import ru.chess.chessapi.entity.UserRoomCandidateEntity
import java.time.LocalDateTime

data class UserRoomCandidateResponseDto(
    val id: String,
    val user: UserDto,
    val createdAt: LocalDateTime
)

fun UserRoomCandidateEntity.toDto(): UserRoomCandidateResponseDto = UserRoomCandidateResponseDto(
    id = this.id.toString(),
    user = this.user.toDto(),
    createdAt = this.createdAt!!
)
