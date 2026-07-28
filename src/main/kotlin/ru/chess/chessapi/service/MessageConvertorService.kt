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
    // todo: get messageType value -> convert or null
    // todo это точно не правильно работает, первый удачный маппинг возьмет и сработает, но значит ли это, что сообщение подошло под тип?
    // todo в самом сообщении нет уникальных полей, которые могли бы быть однозначно интерпретироваться
    fun convertMessageDtoFromTextMessage(message: TextMessage): MessageDto? {
        return getRequestForRoomMessage(message) ?:
            getRoomFoundMessage(message) ?:
            getMoveMessage(message) ?:
            getMatchFinishedMessage(message) ?:
            getRequestForRoomCancelMessage(message) ?:
            getWsRetryMessage(message) ?:
            getPingMessage(message)
    }

    private fun getRequestForRoomMessage(message: TextMessage): RequestForRoomMessageDto? {
        return try {
            val message = mapper.readValue<RequestForRoomMessageDto>(message.payload)
            if (message.messageType == MessageType.REQUEST_FOR_ROOM)
                message
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getRoomFoundMessage(message: TextMessage): RoomFoundMessageDto? {
        return try {
            val message = mapper.readValue<RoomFoundMessageDto>(message.payload)
            if (message.messageType == MessageType.ROOM_FOUND)
                message
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getMoveMessage(message: TextMessage): MoveMessageDto? {
        return try {
            val message = mapper.readValue<MoveMessageDto>(message.payload)
            if (message.messageType == MessageType.CHESS_MOVE)
                message
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getMatchFinishedMessage(message: TextMessage): MatchFinishedMessageDto? {
        return try {
            val message = mapper.readValue<MatchFinishedMessageDto>(message.payload)
            if (message.messageType == MessageType.MATCH_FINISHED)
                message
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getRequestForRoomCancelMessage(message: TextMessage): RequestForRoomCancelDto? {
        return try {
            val message = mapper.readValue<RequestForRoomCancelDto>(message.payload)
            if (message.messageType == MessageType.REQUEST_FOR_ROOM_CANCEL)
                message
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getWsRetryMessage(message: TextMessage): RequestForWsRetryDto? {
        return try {
            val message = mapper.readValue<RequestForWsRetryDto>(message.payload)
            if (message.messageType == MessageType.WS_RETRY)
                message
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getPingMessage(message: TextMessage): Ping? {
        return try {
            val message = mapper.readValue<Ping>(message.payload)
            if (message.messageType == MessageType.PING)
                message
            else null
        } catch (_: Exception) {
            null
        }
    }
}
