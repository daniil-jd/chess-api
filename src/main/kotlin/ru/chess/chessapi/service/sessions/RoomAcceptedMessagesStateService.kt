package ru.chess.chessapi.service.sessions

import org.springframework.stereotype.Service
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.web.websocket.message.MoveAcceptedMessage
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service
class RoomAcceptedMessagesStateService {

    private var roomIdToMoveAcceptedMessages = ConcurrentHashMap<UUID, CopyOnWriteArrayList<MoveAcceptedMessage>>()

    fun addMessage(
        roomId: UUID,
        message: MoveAcceptedMessage
    ) {
        roomIdToMoveAcceptedMessages.computeIfAbsent(roomId) {
            CopyOnWriteArrayList<MoveAcceptedMessage>()
        }.add(message)
    }

    fun getMessagesByRoomId(
        rooId: UUID
    ): List<MoveAcceptedMessage> {
        return roomIdToMoveAcceptedMessages[rooId]?.toList() ?: emptyList()
    }

    fun getMessageByRoomIdAndMoveId(
        rooId: UUID,
        moveId: UUID,
    ): MoveAcceptedMessage? {
        val moveIdToMove: Map<UUID, MoveAcceptedMessage?>? = roomIdToMoveAcceptedMessages[rooId]?.associate {
            it.moveId to it
        }
        return moveIdToMove?.get(moveId)
    }

    fun getLastMoveByRoom(
        rooId: UUID
    ): Int? {
        return roomIdToMoveAcceptedMessages[rooId]
            ?.toList()
            ?.maxByOrNull { it.ply }
            ?.ply
    }

    fun clearByRoomId(rooId: UUID) {
        roomIdToMoveAcceptedMessages.remove(rooId)
    }

}
