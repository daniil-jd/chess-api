package ru.chess.chessapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import ru.chess.chessapi.web.websocket.message.*
import ru.chess.chessapi.web.websocket.message.enums.MessageType

@Service
class MessageConvertorService(
    private val mapper: ObjectMapper
) {

    fun convertMessageDtoFromTextMessage(message: TextMessage): MessageDto? {
        return getRequestForRoomMessage(message) ?:
            getRoomFoundMessage(message) ?:
            getMoveMessage(message) ?:
            getMatchFinishedMessage(message) ?:
            getRequestForRoomCancelMessage(message) ?:
            getWsRetryMessage(message) ?:
            getPingMessage(message)
    }

    private fun getRequestForRoomMessage(textMessage: TextMessage): RequestForRoomMessageDto? {
        return try {
            val message = mapper.readValue<RequestForRoomMessageDto>(textMessage.payload)
            if (message.messageType == MessageType.REQUEST_FOR_ROOM)
                message
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getRoomFoundMessage(textMessage: TextMessage): RoomFoundMessageDto? {
        return try {
            val message = mapper.readValue<RoomFoundMessageDto>(textMessage.payload)
            if (message.messageType == MessageType.ROOM_FOUND)
                message
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getMoveMessage(textMessage: TextMessage): MoveMessageDto? {
        return try {
            val message = mapper.readValue<MoveMessageDto>(textMessage.payload)
            if (message.messageType == MessageType.CHESS_MOVE)
                message
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getMatchFinishedMessage(textMessage: TextMessage): MatchFinishedMessageDto? {
        return try {
            val message = mapper.readValue<MatchFinishedMessageDto>(textMessage.payload)
            if (message.messageType == MessageType.MATCH_FINISHED)
                message
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getRequestForRoomCancelMessage(textMessage: TextMessage): RequestForRoomCancelDto? {
        return try {
            val message = mapper.readValue<RequestForRoomCancelDto>(textMessage.payload)
            if (message.messageType == MessageType.REQUEST_FOR_ROOM_CANCEL)
                message
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getWsRetryMessage(textMessage: TextMessage): RequestForWsRetryDto? {
        return try {
            val message = mapper.readValue<RequestForWsRetryDto>(textMessage.payload)
            if (message.messageType == MessageType.WS_RETRY)
                message
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getPingMessage(textMessage: TextMessage): Ping? {
        return try {
            val message = mapper.readValue<Ping>(textMessage.payload)
            if (message.messageType == MessageType.PING)
                message
            else null
        } catch (_: Exception) {
            null
        }
    }
}
