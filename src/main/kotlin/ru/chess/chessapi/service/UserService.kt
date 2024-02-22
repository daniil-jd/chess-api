package ru.chess.chessapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.websocket.message.enums.SideType
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.repository.UserRepository
import java.util.*

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository
) {
    fun save(userEntity: UserEntity): UserEntity = userRepository.save(userEntity)

    fun findUserByName(name: String): UserEntity? = userRepository.findByUsername(name)

    fun findById(userId: UUID): UserEntity? {
        return userRepository.findById(userId).orElse(null)
    }
}
