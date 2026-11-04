package ru.chess.chessapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import ru.chess.chessapi.entity.RoomEntity
import java.time.Instant
import java.util.UUID

interface RoomRepository : JpaRepository<RoomEntity, UUID> {

    @Query(
        nativeQuery = true,
        value = """
            select re.* from public.chess_rooms_2 re
            where re.room_status = 'ACTIVE' and (
                re.user_1 = :userId1 and re.user_2 = :userId2 or 
                re.user_2 = :userId1 and re.user_1 = :userId2
                )
            for update
        """
    )
    fun findAllByUser1AndUser2ForUpdate(userId1: UUID, userId2: UUID): List<RoomEntity>

    @Query(
        nativeQuery = true,
        value = """
            select * from public.chess_rooms_2 re
            where re.room_status != 'ACTIVE' and (
                re.user_1 = (:userId) or 
                re.user_2 = (:userId)
                )
        """
    )
    fun findByUser(userId: UUID): RoomEntity?

    @Query(
        nativeQuery = true,
        value = """
            select * from public.chess_rooms_2 re
            where re.room_status = 'ACTIVE' and re.deadline_at > :currentInstant and (re.user_1 = (:userId) or re.user_2 = (:userId))
            for update
        """
    )
    fun findActiveByUserForUpdate(userId: UUID, currentInstant: Instant): RoomEntity?

    @Query(
        nativeQuery = true,
        value = """
            select * from public.chess_rooms_2 re
            where re.room_status = 'ACTIVE' and re.deadline_at > :currentInstant and re.id = :id
            for update
        """
    )
    fun findActiveForUpdate(id: UUID, currentInstant: Instant): RoomEntity?

    @Query(
        nativeQuery = true,
        value = """
            select re.* from public.chess_rooms_2 re
            where re.room_status != 'ACTIVE' and (re.user_1 = (:userId) or re.user_2 = (:userId)) and re.game_type = :gameType
            order by re.created_at desc
            limit 30
        """
    )
    fun findLatest30RoomsByUser(userId: UUID, gameType: String): List<RoomEntity>

    @Query(
        nativeQuery = true,
        value = """
            select count(*) from public.chess_rooms_2 re
            where re.room_status != 'ACTIVE' and (
                re.user_1 = (:userId) or 
                re.user_2 = (:userId)
                )
        """
    )
    fun countByUser(userId: UUID): Long

    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            update public.chess_rooms_2
            set room_status = 'EXPIRED', 
                updated_at = :currentInstant
            where room_status = 'ACTIVE' 
                and deadline_at <= :currentInstant;
        """
    )
    fun markExpiredDeadline(currentInstant: Instant)
}