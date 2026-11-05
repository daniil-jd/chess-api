package ru.chess.chessapi.repository

import jakarta.transaction.Transactional
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.repository.PagingAndSortingRepository
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.entity.UserRoomCandidateEntity
import java.time.LocalDateTime
import java.util.UUID

interface UserRoomCandidateRepository : JpaRepository<UserRoomCandidateEntity, UUID>, PagingAndSortingRepository<UserRoomCandidateEntity, UUID> {
    fun findByUser(user: UserEntity): UserRoomCandidateEntity?
    fun findAllByActiveUntilIsGreaterThanEqual(activeUntil: LocalDateTime, page: Pageable): Slice<UserRoomCandidateEntity>
    @Modifying
    @Transactional
    fun deleteAllByActiveUntilGreaterThan(actualUntil: LocalDateTime): Int
    @Modifying
    @Transactional
    fun deleteByUser(user: UserEntity): Int
}
