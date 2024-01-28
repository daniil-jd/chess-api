package ru.chess.chessapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.repository.RoomRepository
import java.util.*

@Service
class RoomService(
    private val roomRepository: RoomRepository
) {

    @Transactional
    fun createRoom(user1: UserEntity, user2: UserEntity): RoomEntity {
        val rooms = roomRepository.findByUser1AndUser2(user1, user2)
        if (rooms.isNotEmpty()) {
            throw Exception("Room already exist for user1 ($user1), user2($user2), room: $rooms")
        }

        return roomRepository.save(RoomEntity(user1 = user1, user2 = user2))
    }

    fun findUserInRoom(room: RoomEntity, authorName: String, isAuthor: Boolean): Optional<UserEntity> {
        if (room.user1.username == authorName) {
            if (isAuthor) {
                return room.user2.let { Optional.of(room.user2!!) }
            } else {
                return Optional.of(room.user1)
            }
        } else if (room.user2?.username == authorName) {
            if (isAuthor) {
                Optional.of(room.user1)
            } else {
                return room.user2.let { Optional.of(room.user2!!) }
            }
        }
        return Optional.empty()
    }

    fun findRoomById(roomId: UUID): Optional<RoomEntity> {
        return roomRepository.findById(roomId)
    }
}
