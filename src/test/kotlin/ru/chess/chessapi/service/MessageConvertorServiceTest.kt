package ru.chess.chessapi.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.web.socket.TextMessage
import ru.chess.chessapi.TestData.Models.Message.SERIALIZED_MESSAGE
import ru.chess.chessapi.TestData.Models.Message.moveMessage
import ru.chess.chessapi.TestData.Models.Message.matchFinishedMessage
import ru.chess.chessapi.TestData.Models.Message.requestForRoomMessage
import ru.chess.chessapi.TestData.Models.Message.requestForWsRetryMessage
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Rooms.CURRENT_INSTANT
import ru.chess.chessapi.TestData.Sessions.SESSION_ID
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USERNAME_2
import ru.chess.chessapi.web.websocket.message.*
import ru.chess.chessapi.web.websocket.message.enums.*

@ExtendWith(MockKExtension::class)
class MessageConvertorServiceTest {
    @InjectMockKs
    private lateinit var service: MessageConvertorService
    @MockK(relaxUnitFun = true)
    private lateinit var mapper: ObjectMapper

    @ParameterizedTest
    @MethodSource("messages")
    fun `convertMessageDtoFromTextMessage - finds supported type after failed conversions`(expected: MessageDto) {
        every { mapper.readValue<Any>(SERIALIZED_MESSAGE, any<TypeReference<Any>>()) } answers {
            if (secondArg<TypeReference<Any>>().type == expected.javaClass) expected
            else throw IllegalArgumentException("Different message shape")
        }

        service.convertMessageDtoFromTextMessage(TextMessage(SERIALIZED_MESSAGE)) shouldBe expected

        val attempts = supportedMessages().indexOfFirst { it.javaClass == expected.javaClass } + 1
        verify(exactly = attempts) { mapper.readValue<Any>(SERIALIZED_MESSAGE, any<TypeReference<Any>>()) }
    }

    @Test
    fun `convertMessageDtoFromTextMessage - returns null when every conversion fails`() {
        every { mapper.readValue<Any>(SERIALIZED_MESSAGE, any<TypeReference<Any>>()) } throws IllegalArgumentException("Invalid JSON")

        service.convertMessageDtoFromTextMessage(TextMessage(SERIALIZED_MESSAGE)) shouldBe null

        verify(exactly = 7) { mapper.readValue<Any>(SERIALIZED_MESSAGE, any<TypeReference<Any>>()) }
    }

    @Test
    fun `convertMessageDtoFromTextMessage - rejects DTOs with mismatched message types`() {
        val mismatches = listOf(
            requestForRoomMessage().copy(messageType = MessageType.PONG),
            roomFoundMessage().copy(messageType = MessageType.PONG),
            moveMessage().copy(messageType = MessageType.PONG),
            matchFinishedMessage().copy(messageType = MessageType.PONG),
            RequestForRoomCancelDto(MessageType.PONG),
            requestForWsRetryMessage().copy(messageType = MessageType.PONG),
            Ping(MessageType.PONG, USER_ID_1, ROOM_ID)
        ).associateBy { it.javaClass }
        every { mapper.readValue<Any>(SERIALIZED_MESSAGE, any<TypeReference<Any>>()) } answers {
            mismatches[secondArg<TypeReference<Any>>().type]!!
        }

        service.convertMessageDtoFromTextMessage(TextMessage(SERIALIZED_MESSAGE)) shouldBe null

        verify(exactly = 7) { mapper.readValue<Any>(SERIALIZED_MESSAGE, any<TypeReference<Any>>()) }
    }

    companion object {
        private fun roomFoundMessage() = RoomFoundMessageDto(
            backendUserId = USER_ID_1, room = ROOM_ID, opponentName = USERNAME_2,
            playerSide = SideType.WHITE, sessionId = SESSION_ID, matchServerTimeMs = CURRENT_INSTANT.toEpochMilli()
        )

        private fun supportedMessages(): List<MessageDto> = listOf(
            requestForRoomMessage(), roomFoundMessage(), moveMessage(), matchFinishedMessage(),
            RequestForRoomCancelDto(MessageType.REQUEST_FOR_ROOM_CANCEL), requestForWsRetryMessage(),
            Ping(MessageType.PING, USER_ID_1, ROOM_ID)
        )

        @JvmStatic
        fun messages() = supportedMessages().stream()
    }
}
