package ru.chess.chessapi.repository

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.BaseSpringTestFull
import ru.chess.chessapi.entity.MatchmakingLockEntity
import java.sql.SQLException
import javax.sql.DataSource

class MatchmakingLockRepositoryTest : BaseSpringTestFull() {
    @Autowired
    private lateinit var repository: MatchmakingLockRepository

    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    @Transactional
    fun `lockForMatchmaking - holds the seeded row until the caller transaction ends`() {
        val lockId = MatchmakingLockEntity().id
        repository.lockForMatchmaking()?.id shouldBe lockId

        // A separate database connection must be unable to lock the same row.
        dataSource.connection.use { connection ->
            connection.prepareStatement("select id from matchmaking_lock where id = ? for update nowait").use { query ->
                query.setInt(1, lockId)
                shouldThrow<SQLException> { query.executeQuery().close() }.sqlState shouldBe LOCK_NOT_AVAILABLE
            }
        }
    }

    @Test
    fun `lockForMatchmaking - requires a caller transaction`() {
        shouldThrow<IllegalTransactionStateException> { repository.lockForMatchmaking() }
    }

    private companion object {
        const val LOCK_NOT_AVAILABLE = "55P03"
    }
}
