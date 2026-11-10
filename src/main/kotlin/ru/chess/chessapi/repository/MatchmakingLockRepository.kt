package ru.chess.chessapi.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import ru.chess.chessapi.entity.MatchmakingLockEntity

interface MatchmakingLockRepository : JpaRepository<MatchmakingLockEntity, Int> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MatchmakingLockEntity l where l.id = 1")
    fun lockForMatchmaking(): MatchmakingLockEntity?
}
