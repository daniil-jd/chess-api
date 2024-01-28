package ru.chess.chessapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableScheduling

@EnableJpaAuditing
@SpringBootApplication
class ChessApiApplication

fun main(args: Array<String>) {
	runApplication<ChessApiApplication>(*args)
}
