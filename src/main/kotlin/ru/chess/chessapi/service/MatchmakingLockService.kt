package ru.chess.chessapi.service

import org.springframework.stereotype.Service
import ru.chess.chessapi.repository.MatchmakingLockRepository

@Service
class MatchmakingLockService(
    private val matchmakingLockRepository: MatchmakingLockRepository,
) {
    fun lockMatchmaking() {
        // Hold the lock until the caller's transaction commits or rolls back.
        checkNotNull(matchmakingLockRepository.lockForMatchmaking()) {
            "Missing matchmaking_lock row with id=1"
        }
    }
}
