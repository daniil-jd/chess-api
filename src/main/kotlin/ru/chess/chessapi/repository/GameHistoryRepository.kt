package ru.chess.chessapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.chess.chessapi.entity.GameHistory
import ru.chess.chessapi.entity.GameHistoryId
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import java.util.*

interface GameHistoryRepository : JpaRepository<GameHistory, GameHistoryId> {

    @Query(
        nativeQuery = true,
        value = """
            select COALESCE(MAX(match_number), 0)
            from game_history
            where user_id = :userId
        """
    )
    fun maxMatchNumberForUser(userId: UUID): Int

    fun findByUserAndRoom(user: UserEntity, room: RoomEntity): GameHistory?

    @Query(
        nativeQuery = true,
        value = """
            select gh.* from public.game_history gh
            where gh.user_id = :userId and gh.game_type = :gameType
            order by gh.match_number desc
            limit 30
        """
    )
    fun findLatest30RoomsByUserAndGameType(userId: UUID, gameType: String): List<GameHistory>

    fun findAllByUserAndFavourite(user: UserEntity, favourite: Boolean): List<GameHistory>
}