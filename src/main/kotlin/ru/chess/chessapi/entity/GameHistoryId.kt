package ru.chess.chessapi.entity

import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.*

@Embeddable
data class GameHistoryId(
    var userId: UUID,
    var roomId: UUID
) : Serializable