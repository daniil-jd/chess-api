package ru.chess.chessapi.service

import org.springframework.stereotype.Service
import ru.chess.chessapi.entity.FavouriteRoomEntity
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.repository.FavouriteRoomRepository

@Service
class FavouriteRoomService(
    private val favouriteRoomRepository: FavouriteRoomRepository
) {

    fun findByUserAndRoom(user: UserEntity, room: RoomEntity): FavouriteRoomEntity? {
        return favouriteRoomRepository.findByUserAndRoom(user, room)
    }

    fun delete(favouriteRoom: FavouriteRoomEntity) {
        favouriteRoomRepository.delete(favouriteRoom)
    }

    fun create(user: UserEntity, room: RoomEntity): FavouriteRoomEntity {
        return favouriteRoomRepository.save(FavouriteRoomEntity(user = user, room = room))
    }

    fun findByUserAndRooms(user: UserEntity, rooms: List<RoomEntity>): List<FavouriteRoomEntity> {
        return favouriteRoomRepository.findByUserAndRoomIn(user, rooms)
    }

}
