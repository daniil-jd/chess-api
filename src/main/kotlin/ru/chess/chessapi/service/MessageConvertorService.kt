package ru.chess.chessapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import ru.chess.chessapi.websocket.message.*

@Service
class MessageConvertorService(
    private val mapper: ObjectMapper
) {
    // todo: get messageType value -> convert or null
    fun convertMessageDtoFromTextMessage(message: TextMessage): MessageDto? {
        return getRequestForRoomMessage(message) ?:
            getRoomFoundMessage(message) ?:
            getMoveMessage(message) ?:
            getMatchFinishedMessage(message) ?:
            getRequestForRoomCancelMessage(message)
    }

    private fun getRequestForRoomMessage(message: TextMessage): RequestMessageDto? {
        return try {
            mapper.readValue<RequestMessageDto>(message.payload)
        } catch (ex: Exception) {
            null
        }
    }

    private fun getRoomFoundMessage(message: TextMessage): RoomFoundMessageDto? {
        return try {
            mapper.readValue<RoomFoundMessageDto>(message.payload)
        } catch (ex: Exception) {
            null
        }
    }

    private fun getMoveMessage(message: TextMessage): MoveMessageDto? {
        return try {
            mapper.readValue<MoveMessageDto>(message.payload)
        } catch (ex: Exception) {
            null
        }
    }

    private fun getMatchFinishedMessage(message: TextMessage): MatchFinishedMessageDto? {
        return try {
            mapper.readValue<MatchFinishedMessageDto>(message.payload)
        } catch (ex: Exception) {
            null
        }
    }

    private fun getRequestForRoomCancelMessage(message: TextMessage): RequestForRoomCancelDto? {
        return try {
            mapper.readValue<RequestForRoomCancelDto>(message.payload)
        } catch (ex: Exception) {
            null
        }
    }
}
