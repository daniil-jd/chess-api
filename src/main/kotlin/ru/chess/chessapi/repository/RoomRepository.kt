package ru.chess.chessapi.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.web.websocket.message.enums.GameType
import java.util.UUID

interface RoomRepository : JpaRepository<RoomEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from RoomEntity room where room.id = :roomId")
    fun findByIdForUpdate(@Param("roomId") roomId: UUID): RoomEntity?

    fun findAllByGameTypeAndWinTypeIsNull(gameType: GameType): List<RoomEntity>

    @Query(
        nativeQuery = true,
        value = """
            select re.* from public.chess_rooms_2 re
            where (re.user_1 = :userId or re.user_2 = :userId) and re.game_type = 'ONLINE'
            order by re.created_at desc
            limit 1
        """
    )
    fun findLatestOnlineRoomByUser(@Param("userId") userId: UUID): RoomEntity?

    @Query(
        nativeQuery = true,
        value = """
            select re.* from public.chess_rooms_2 re
            where re.user_1 = :userId1 and re.user_2 = :userId2 and re.win_type is null or 
                re.user_2 = :userId1 and re.user_1 = :userId2 and re.win_type is null
        """
    )
    fun findAllByUser1AndUser2(userId1: UUID, userId2: UUID): List<RoomEntity>

    @Query(
        nativeQuery = true,
        value = """
            select re.* from public.chess_rooms_2 re
            where re.win_type is not null and (re.user_1 = (:userId) or re.user_2 = (:userId)) and re.game_type = :gameType
            order by re.created_at desc
            limit 30
        """
    )
    fun findLatest30RoomsByUser(userId: UUID, gameType: String): List<RoomEntity>

    @Query(
        nativeQuery = true,
        value = """
            select count(*) from public.chess_rooms_2 re
            where re.user_1 = (:userId) and re.win_type is not null
                or re.user_2 = (:userId) and re.win_type is not null
        """
    )
    fun countByUser(userId: UUID): Long

    @Query(
        nativeQuery = true,
        value = """
            select * from public.chess_rooms_2 re
            where re.user_1 = (:userId) and re.win_type is not null
                or re.user_2 = (:userId) and re.win_type is not null
        """
    )
    fun findByUser(userId: UUID): RoomEntity?

    @Query(
        nativeQuery = true,
        value = """
            select * from public.chess_rooms_2 re
            where re.user_1 = (:userId) and re.win_type is null
                or re.user_2 = (:userId) and re.win_type is null
        """
    )
    fun findNotFinishedRoomByUser(userId: UUID): RoomEntity?
}
