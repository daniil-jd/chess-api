package ru.chess.chessapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.dto.message.enums.SideType
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.repository.UserRepository
import java.util.*

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository
) {
    fun save(userEntity: UserEntity): UserEntity = userRepository.save(userEntity)

    fun findUserByName(name: String): Optional<UserEntity> = userRepository.findByUsername(name)

    fun findById(userId: UUID): UserEntity? {
        return userRepository.findById(userId).orElse(null)
    }

    fun findOrCreate(userNameOrUuid: String?, sideType: SideType): UserEntity {
        if (userNameOrUuid == null) {
            return createUserDefault(sideType, null)
        }
        val userId = getUuidFromString(userNameOrUuid)
        if (userId != null) {
            val user = userRepository.findById(userId)
            if (user.isEmpty) {
                return createUserDefault(sideType, userNameOrUuid)
            }
            return user.get()
        } else {
            // todo: fix this, should search by username too
            return createUserDefault(sideType, userNameOrUuid)
        }
    }

    fun createUserDefault(sideType: SideType, username: String?): UserEntity {
        return save(UserEntity(
            username = username ?: "player_${UUID.randomUUID()}",
            userSide = sideType,
            isEnabled = true
        ))
    }

    fun replaceRandomSide(user: UserEntity, sideType: SideType): UserEntity {
        user.userSide = sideType
        return save(user)
    }

    private fun getUuidFromString(candidate: String): UUID? {
        return try {
            UUID.fromString(candidate)
        } catch (ex: IllegalArgumentException) {
            null
        }
    }
}
