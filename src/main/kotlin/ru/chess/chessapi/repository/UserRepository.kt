package ru.chess.chessapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.chess.chessapi.entity.UserEntity
import java.util.UUID

interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByUsername(username: String): UserEntity?

    fun findBySignature(signature: String): UserEntity?

    @Query(
        """
            from UserEntity ue where ue.isBot = :isBot
        """
    )
    fun findAllByBot(isBot: Boolean): List<UserEntity>
}