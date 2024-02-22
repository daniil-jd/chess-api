package ru.chess.chessapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.chess.chessapi.entity.UserEntity
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByUsername(username: String): UserEntity?
}