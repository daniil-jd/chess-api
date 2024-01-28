package ru.chess.chessapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.chess.chessapi.entity.AuthenticationTokenEntity
import ru.chess.chessapi.entity.UserEntity
import java.util.*

interface AuthenticationTokenRepository : JpaRepository<AuthenticationTokenEntity, UUID> {
    fun deleteAllByUser(user: UserEntity)
}