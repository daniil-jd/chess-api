package ru.chess.chessapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.chess.chessapi.entity.util.RegistrationTokenEntity
import java.util.UUID

interface RegistrationTokenRepository : JpaRepository<RegistrationTokenEntity, UUID> {
    fun findAllByUserId(id: UUID): List<RegistrationTokenEntity>
}