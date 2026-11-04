package ru.chess.chessapi.service

import org.springframework.stereotype.Service
import java.time.Instant

@Service
class TimeService {

    fun getRoomDeadLineInstant(): Instant {
        return Instant.now().plusSeconds(60)
    }

    fun getNowInstant(): Instant {
        return Instant.now()
    }

}