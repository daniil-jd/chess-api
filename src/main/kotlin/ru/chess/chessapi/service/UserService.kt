package ru.chess.chessapi.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.repository.UserRepository
import java.util.*

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository
) {

    private val logger = KotlinLogging.logger {}

    private companion object {
        const val WHITE_BOT_NAME = "white_bot"
        const val BLACK_BOT_NAME = "black_bot"
    }

    fun save(userEntity: UserEntity): UserEntity = userRepository.save(userEntity)

    fun findUserByName(name: String): UserEntity? = userRepository.findByUsername(name)

    fun findById(userId: UUID): UserEntity? {
        return userRepository.findById(userId).orElse(null).also {
            logger.info { "Search user by userId: $userId, result: $it" }
        }
    }

    fun createUser(username: String, signature: String?): UserEntity {
        return userRepository.save(
            UserEntity(
                username = username,
                signature = signature,
                rating = 0L
            )
        ).also { logger.info { "User successfully created: $it" } }
    }

    fun findBySignature(signature: String): UserEntity? {
        return userRepository.findBySignature(signature).also {
            logger.info { "Search user by signature: $signature, result: $it" }
        }
    }

    fun findBotByColor(isWhite: Boolean): UserEntity {
        val bots = userRepository.findAllByBot(isBot = true)
        if (bots.isEmpty() || bots.size != 2) {
            throw Exception("Exception during search bots, there is no white and black bots: $bots")
        }

        if (isWhite) {
            return when {
                bots.first().username == WHITE_BOT_NAME -> bots.first()
                bots.last().username == WHITE_BOT_NAME -> bots.last()
                else -> {
                    throw Exception("Exception during search bots, there is no white bot: $bots")
                }
            }
        } else {
            return when {
                bots.first().username == BLACK_BOT_NAME -> bots.first()
                bots.last().username == BLACK_BOT_NAME -> bots.last()
                else -> {
                    throw Exception("Exception during search bots, there is no black bot: $bots")
                }
            }
        }
    }
}
