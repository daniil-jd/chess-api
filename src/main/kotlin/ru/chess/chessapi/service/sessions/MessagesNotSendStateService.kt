package ru.chess.chessapi.service.sessions

import org.springframework.stereotype.Service
import ru.chess.chessapi.web.websocket.message.MessageDto
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service
class MessagesNotSendStateService {

    private var userIdToMessagesNotSend = ConcurrentHashMap<UUID, CopyOnWriteArrayList<MessageDto>>()

    fun addMessage(
        userId: UUID,
        message: MessageDto
    ) {
        userIdToMessagesNotSend.computeIfAbsent(userId) {
            CopyOnWriteArrayList()
        }.add(message)
    }

    fun getMessagesByUserId(
        userId: UUID
    ): List<MessageDto> {
        return userIdToMessagesNotSend[userId] ?: emptyList()
    }

    fun cleanByUserid(userId: UUID) {
        userIdToMessagesNotSend.remove(userId)
    }
}