package ru.chess.chessapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "matchmaking_lock")
class MatchmakingLockEntity(
    @Id
    var id: Int = 1
)
