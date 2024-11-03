package ru.chess.chessapi.model

import java.io.Serializable
import java.util.*

data class CandidatePair<out A, out B>(
        val first: A,
        val second: B
) : Serializable {
    override fun toString(): String = "($first, $second)"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CandidatePair<*, *>

        if (first != other.first) return false
        if (second != other.second) return false

        return first == other.first && second == other.second ||
                first == other.second && second == other.first
    }

    override fun hashCode(): Int {
        return Objects.hashCode(first) + Objects.hashCode(second)
    }
}
