package ru.chess.chessapi.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import ru.chess.chessapi.entity.MatchmakingLockEntity
import ru.chess.chessapi.repository.MatchmakingLockRepository

class MatchmakingLockServiceTest {
    private val repository = mockk<MatchmakingLockRepository>()
    private val service = MatchmakingLockService(repository)

    @Test
    fun `lockMatchmaking - acquires the shared lock`() {
        every { repository.lockForMatchmaking() } returns MatchmakingLockEntity()

        service.lockMatchmaking()

        verify(exactly = 1) { repository.lockForMatchmaking() }
    }

    @Test
    fun `lockMatchmaking - fails if the lock row is missing`() {
        every { repository.lockForMatchmaking() } returns null

        shouldThrow<IllegalStateException> { service.lockMatchmaking() }
            .message shouldBe "Missing matchmaking_lock row with id=1"
    }
}
