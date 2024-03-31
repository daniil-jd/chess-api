package ru.chess.chessapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import java.util.UUID

interface RoomRepository : JpaRepository<RoomEntity, UUID> {

    // todo: doesn't work, something wrong with relatives
    @Query(
        """
            select re from RoomEntity re
            where re.user1 = (:user1) and re.user2 = (:user2) and re.winnerSide is null or 
                re.user2 = (:user1) and re.user1 = (:user2) and re.winnerSide is null
        """
    )
    fun findByUser1AndUser2(user1: UserEntity, user2: UserEntity): List<RoomEntity>

    @Query(
        nativeQuery = true,
        value = """
            select re from public.chess_rooms_2 re
            where re.user_1 = :userId1 and re.user_2 = :userId2 and re.winner_side is null or 
                re.user_2 = :userId1 and re.user_1 = :userId2 and re.winner_side is null
        """
    )
    fun findAllByUser1AndUser2(userId1: UUID, userId2: UUID): List<RoomEntity>
}