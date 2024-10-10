package ru.chess.chessapi.service

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.entity.UserRoomCandidateEntity
import ru.chess.chessapi.repository.UserRoomCandidateRepository
import ru.chess.chessapi.websocket.message.enums.SideType
import java.time.LocalDateTime

@Service
class UserRoomCandidateService(
    private val userRoomCandidateRepository: UserRoomCandidateRepository
) {
    companion object {
        const val BATCH_SIZE = 30
    }

    fun createUserRoomCandidate(user: UserEntity, sideType: SideType): UserRoomCandidateEntity {
        val userRoomCandidate = userRoomCandidateRepository.findByUser(user)
        if (userRoomCandidate != null) {
            val currentLocalDateTime = LocalDateTime.now()
            with(userRoomCandidate) {
                if (this.activeUntil!! > currentLocalDateTime) {
                    userRoomCandidateRepository.delete(this)
                    return userRoomCandidateRepository.save(
                        UserRoomCandidateEntity(
                            user = user,
                            userSide = sideType,
                            createdAt = LocalDateTime.now()
                        )
                    )
                }
                return this
            }
        } else {
            return userRoomCandidateRepository.save(
                UserRoomCandidateEntity(
                    user = user,
                    userSide = sideType,
                    createdAt = LocalDateTime.now()
                )
            )
        }
    }

    fun deleteCandidates(candidates: List<UserRoomCandidateEntity>) {
        userRoomCandidateRepository.deleteAll(candidates)
    }

    fun findActiveUserRoomCandidates(): MutableList<UserRoomCandidateEntity> {
        val currentTime = LocalDateTime.now()
        var slice = userRoomCandidateRepository.findAllByActiveUntilIsGreaterThanEqual(
            currentTime, PageRequest.of(0, BATCH_SIZE)
        )
        val result = slice.content
        while (slice.hasNext()) {
            slice = userRoomCandidateRepository.findAllByActiveUntilIsGreaterThanEqual(currentTime, slice.nextPageable())
            result.addAll(slice.content)
        }
        return result.toMutableList()
    }

    fun deleteAllOverdue(): Int {
        return userRoomCandidateRepository.deleteAllByActiveUntilGreaterThan(LocalDateTime.now())
    }

    fun deleteByUser(user: UserEntity): Int {
        return userRoomCandidateRepository.deleteByUser(user)
    }
}