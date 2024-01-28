package ru.chess.chessapi.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.PagingAndSortingRepository
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.entity.UserRoomCandidateEntity
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

interface UserRoomCandidateRepository : PagingAndSortingRepository<UserRoomCandidateEntity, UUID> {
    fun findByUser(user: UserEntity): Optional<UserRoomCandidateEntity>
    fun findAllByActualUntilLessThanEqual(actualUntil: LocalDateTime, page: Pageable): Slice<UserRoomCandidateEntity>
    fun findAllByActualUntilIsGreaterThanEqual(actualUntil: LocalDateTime, page: Pageable): Slice<UserRoomCandidateEntity>
    @Modifying
    fun deleteAllByActualUntilGreaterThan(actualUntil: LocalDateTime): Int
    @Modifying
    fun deleteByUser(user: UserEntity): Int
}