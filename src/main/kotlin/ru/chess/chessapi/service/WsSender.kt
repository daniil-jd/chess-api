package ru.chess.chessapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.web.websocket.message.MessageDto
import java.io.IOException
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

@Service
class WsSender(
    private val mapper: ObjectMapper
) {

    private val logger = KotlinLogging.logger {}

    private fun sendMessage(
        userId: UUID,
        message: MessageDto,
        wsSession: WebSocketSession
    ) {
//        try {
//            wsSession.sendMessage(TextMessage(mapper.writeValueAsString(message)))
//        } catch (ex: IOException) {
//            logger.warn {
//                "can't send message $message to user ($userId), something wrong with ws session " +
//                    "(id: ${wsSession.id}, is open: ${wsSession.isOpen}), error: ${ex.message}"
//            }
//            // в случае ошибки отправки сохраняем сообщение для попытки ретрая в будущем
//            userIdToMessagesNotSend.computeIfAbsent(userId) { CopyOnWriteArrayList() }.add(message)
//        }
//        logger.info { "successfully sent message to user: $message, user: $userId" }
    }

}