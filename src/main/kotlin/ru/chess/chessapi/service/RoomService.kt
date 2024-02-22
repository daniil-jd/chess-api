package ru.chess.chessapi.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.repository.RoomRepository
import ru.chess.chessapi.websocket.message.enums.SideType
import java.util.*

@Service
class RoomService(
    private val roomRepository: RoomRepository
) {

    @Transactional
    fun createRoom(user1: UserEntity, user1SideType: SideType, user2: UserEntity, user2SideType: SideType): RoomEntity {
        val rooms = roomRepository.findByUser1AndUser2(user1, user2)
        if (rooms.isNotEmpty()) {
            throw Exception("Room already exist for user1 ($user1), user2($user2), room: $rooms")
        }

        return roomRepository.save(
            RoomEntity(
                user1 = user1,
                user1Side = user1SideType,
                user2 = user2,
                user2Side = user2SideType,
                history = null,
                winnerSide = null,
                winType = null
            )
        )
    }

    fun findRoomById(roomId: UUID): RoomEntity? {
        return roomRepository.findByIdOrNull(roomId)
    }

    fun save(room: RoomEntity): RoomEntity {
        return roomRepository.save(room)
    }
}
