package ru.chess.chessapi.service

import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** Queue reads, room creation and account linking must observe one committed matchmaking state. */
@Service
class MatchmakingLock(private val jdbc: JdbcTemplate) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun acquire() {
        jdbc.execute(ConnectionCallback { connection ->
            connection.prepareStatement("select pg_advisory_xact_lock(?, ?)").use {
                it.setInt(1, 0x43485353)
                it.setInt(2, 0x4D415443)
                it.execute()
            }
            Unit
        })
    }
}
