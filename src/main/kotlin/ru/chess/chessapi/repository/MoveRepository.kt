package ru.chess.chessapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.chess.chessapi.entity.MoveEntity
import java.util.UUID

interface MoveRepository : JpaRepository<MoveEntity, UUID> {
    fun findAllByRoomIdOrderByPly(roomId: UUID): List<MoveEntity>

    fun findByRoomIdAndMoveId(roomId: UUID, moveId: UUID): MoveEntity?

    fun countByRoomId(roomId: UUID): Long

    fun findFirstByRoomIdOrderByPlyDesc(roomId: UUID): MoveEntity?
}
