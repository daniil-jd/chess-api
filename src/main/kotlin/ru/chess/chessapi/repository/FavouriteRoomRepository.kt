package ru.chess.chessapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.chess.chessapi.entity.FavouriteRoomEntity
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import java.util.UUID

interface FavouriteRoomRepository : JpaRepository<FavouriteRoomEntity, UUID> {

    fun findByUserAndRoom(user: UserEntity, room: RoomEntity): FavouriteRoomEntity?

    fun findByUserAndRoomIn(user: UserEntity, rooms: List<RoomEntity>): List<FavouriteRoomEntity>

    fun findAllByUser(user: UserEntity): List<FavouriteRoomEntity>
}