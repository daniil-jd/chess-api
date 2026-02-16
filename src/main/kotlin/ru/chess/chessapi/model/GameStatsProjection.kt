package ru.chess.chessapi.model

import java.util.*

interface GameStatsProjection {
    val userId: UUID
    val gameType: String
    val wins: Long
    val losses: Long
    val draws: Long
}
