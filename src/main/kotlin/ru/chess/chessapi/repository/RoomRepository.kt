package ru.chess.chessapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import java.util.Optional
import java.util.UUID

interface RoomRepository : JpaRepository<RoomEntity, UUID> {
    fun findByUser1(user: UserEntity): Optional<RoomEntity>
    fun findByUser2(user: UserEntity): Optional<RoomEntity>
    @Query(
        """
            select re from RoomEntity re 
            where re.user1 in (:users) or re.user2 in (:users)
        """
    )
    fun findByUsers(users: List<UserEntity>): List<RoomEntity>

    @Query(
        """
            select re from RoomEntity re
            where re.user1 = (:user1) and re.user2 = (:user2) or 
                re.user2 = (:user1) and re.user1 = (:user2)
        """
    )
    fun findByUser1AndUser2(user1: UserEntity, user2: UserEntity): List<RoomEntity>
}