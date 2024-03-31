package ru.chess.chessapi.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.repository.PagingAndSortingRepository
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.entity.UserRoomCandidateEntity
import java.time.LocalDateTime
import java.util.UUID

interface UserRoomCandidateRepository : PagingAndSortingRepository<UserRoomCandidateEntity, UUID> {
    fun findByUser(user: UserEntity): UserRoomCandidateEntity?
    fun findAllByActiveUntilLessThanEqual(actualUntil: LocalDateTime, page: Pageable): Slice<UserRoomCandidateEntity>
    fun findAllByActiveUntilIsGreaterThanEqual(actualUntil: LocalDateTime, page: Pageable): Slice<UserRoomCandidateEntity>
    @Modifying
    fun deleteAllByActiveUntilGreaterThan(actualUntil: LocalDateTime): Int
    @Modifying
    fun deleteByUser(user: UserEntity): Int
}