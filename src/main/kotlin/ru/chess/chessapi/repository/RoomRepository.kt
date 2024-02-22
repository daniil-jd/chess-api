package ru.chess.chessapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import java.util.Optional
import java.util.UUID

interface RoomRepository : JpaRepository<RoomEntity, UUID> {

    @Query(
        """
            select re from RoomEntity re
            where re.user1 = (:user1) and re.user2 = (:user2) and re.winnerSide is null or 
                re.user2 = (:user1) and re.user1 = (:user2) and re.winnerSide is null
        """
    )
    fun findByUser1AndUser2(user1: UserEntity, user2: UserEntity): List<RoomEntity>
}