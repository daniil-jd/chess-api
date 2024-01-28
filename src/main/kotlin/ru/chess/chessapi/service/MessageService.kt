package ru.chess.chessapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.entity.MessageEntity
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.repository.MessageRepository

@Service
@Transactional
class MessageService(
    private val repository: MessageRepository
) {
    fun save(messageEntity: MessageEntity) = repository.save(messageEntity)

    fun getAllMessagesByRoom(room: RoomEntity): List<MessageEntity> = repository.findAllByRoom(room)
}