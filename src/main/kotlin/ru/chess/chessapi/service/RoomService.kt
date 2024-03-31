package ru.chess.chessapi.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.repository.RoomRepository
import ru.chess.chessapi.websocket.message.enums.FinishType
import ru.chess.chessapi.websocket.message.enums.GameType
import ru.chess.chessapi.websocket.message.enums.SideType
import java.util.*

@Service
class RoomService(
    private val roomRepository: RoomRepository
) {

    @Transactional
    fun createRoomForOnlineMatch(
        user1: UserEntity,
        user1SideType: SideType,
        user1Name: String,
        user2: UserEntity,
        user2SideType: SideType,
        user2Name: String
    ): RoomEntity {
        val rooms = roomRepository.findAllByUser1AndUser2(user1.id!!, user2.id!!)
        if (rooms.isNotEmpty()) {
            throw Exception("Room already exist for user1 ($user1), user2($user2), room: $rooms")
        }

        return roomRepository.save(
            RoomEntity(
                user1 = user1,
                user1Side = user1SideType,
                user1Name = user1Name,
                user2 = user2,
                user2Side = user2SideType,
                user2Name = user2Name,
                gameType = GameType.ONLINE,
                history = null,
                winnerSide = null,
                winType = null
            )
        )
    }
    @Transactional
    fun createRoomWithHistory(
        user1: UserEntity,
        user1SideType: SideType,
        user1Name: String,
        user2: UserEntity,
        user2SideType: SideType,
        user2Name: String,
        gameType: GameType,
        history: String,
        winnerSide: SideType,
        winType: FinishType
    ): RoomEntity {
        val rooms = roomRepository.findAllByUser1AndUser2(user1.id!!, user2.id!!)
        if (rooms.isNotEmpty()) {
            throw Exception("Room already exist for user1 ($user1), user2($user2), room: $rooms")
        }

        return roomRepository.save(
            RoomEntity(
                user1 = user1,
                user1Side = user1SideType,
                user1Name = user1Name,
                user2 = user2,
                user2Side = user2SideType,
                user2Name = user2Name,
                gameType = gameType,
                history = history,
                winnerSide = winnerSide,
                winType = winType
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
