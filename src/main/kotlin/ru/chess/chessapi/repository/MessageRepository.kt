package ru.chess.chessapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.chess.chessapi.entity.util.MessageEntity
import ru.chess.chessapi.entity.RoomEntity
import java.util.UUID

interface MessageRepository : JpaRepository<MessageEntity, UUID> {
    fun findAllByRoom(room: RoomEntity): List<MessageEntity>
}