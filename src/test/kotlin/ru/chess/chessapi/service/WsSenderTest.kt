package ru.chess.chessapi.service

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Called
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Models.Message.SERIALIZED_MESSAGE
import ru.chess.chessapi.TestData.Models.Message.SERIALIZED_ROOM_MESSAGE_1
import ru.chess.chessapi.TestData.Models.Message.SERIALIZED_ROOM_MESSAGE_2
import ru.chess.chessapi.TestData.Models.Message.SERIALIZED_SESSION_MESSAGE
import ru.chess.chessapi.TestData.Models.Message.moveAcceptedMessage
import ru.chess.chessapi.TestData.Rooms.CREATED_AT
import ru.chess.chessapi.TestData.Rooms.CURRENT_INSTANT
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Sessions.SESSION_ID
import ru.chess.chessapi.TestData.Sessions.SESSION_ID_2
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.TestData.Users.USERNAME_2
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.web.websocket.message.NewWsSession
import ru.chess.chessapi.web.websocket.message.RoomFoundMessageDto
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.io.IOException

@ExtendWith(MockKExtension::class)
class WsSenderTest {
    @InjectMockKs
    private lateinit var wsSender: WsSender
    @MockK(relaxUnitFun = true)
    private lateinit var mapper: ObjectMapper
    @MockK(relaxUnitFun = true)
    private lateinit var userSessionStateService: UserSessionStateService
    @MockK(relaxUnitFun = true)
    private lateinit var timeService: TimeService
    @MockK(relaxUnitFun = true)
    private lateinit var session: WebSocketSession
    @MockK(relaxUnitFun = true)
    private lateinit var otherSession: WebSocketSession

    @BeforeEach
    fun beforeEach() {
        every { session.id } returns SESSION_ID
        every { otherSession.id } returns SESSION_ID_2
        every { session.isOpen } returns true
    }

    @Test
    fun `sendMessage - serializes message and sends text to supplied session`() {
        val message = moveAcceptedMessage()
        every { mapper.writeValueAsString(message) } returns SERIALIZED_MESSAGE

        wsSender.sendMessage(USER_ID_1, message, session)

        verify(exactly = 1) {
            mapper.writeValueAsString(message)
            session.sendMessage(match<TextMessage> { it.payload == SERIALIZED_MESSAGE })
        }
        verify { listOf(userSessionStateService, timeService, otherSession) wasNot Called }
        confirmVerified(mapper)
    }

    @Test
    fun `sendMessage - handles socket IOException`() {
        val message = moveAcceptedMessage()
        every { mapper.writeValueAsString(message) } returns SERIALIZED_MESSAGE
        every { session.sendMessage(any()) } throws IOException("Connection lost")

        wsSender.sendMessage(USER_ID_1, message, session)

        verify(exactly = 1) {
            mapper.writeValueAsString(message)
            session.sendMessage(match<TextMessage> { it.payload == SERIALIZED_MESSAGE })
        }
        verify(exactly = 0) { session.close(any()) }
    }

    @Test
    fun `sendMessage - handles serialization IOException without sending`() {
        val message = moveAcceptedMessage()
        val failure = object : JsonProcessingException("Cannot serialize message") {}
        every { mapper.writeValueAsString(message) } throws failure

        wsSender.sendMessage(USER_ID_1, message, session)

        verify(exactly = 1) { mapper.writeValueAsString(message) }
        verify(exactly = 0) { session.sendMessage(any()) }
    }

    @Test
    fun `sendMessage - propagates non IO failure`() {
        val message = moveAcceptedMessage()
        val failure = IllegalStateException("Session closed")
        every { mapper.writeValueAsString(message) } returns SERIALIZED_MESSAGE
        every { session.sendMessage(any()) } throws failure

        shouldThrow<IllegalStateException> { wsSender.sendMessage(USER_ID_1, message, session) } shouldBe failure
    }

    @Test
    fun `sendMessageIfSessionExist - sends to session found by user id`() {
        val message = moveAcceptedMessage()
        every { userSessionStateService.getByUserId(USER_ID_2) } returns otherSession
        every { mapper.writeValueAsString(message) } returns SERIALIZED_MESSAGE

        wsSender.sendMessageIfSessionExist(USER_ID_2, message)

        verify(exactly = 1) {
            userSessionStateService.getByUserId(USER_ID_2)
            mapper.writeValueAsString(message)
            otherSession.sendMessage(match<TextMessage> { it.payload == SERIALIZED_MESSAGE })
        }
        verify { session wasNot Called }
        confirmVerified(userSessionStateService, mapper, otherSession)
    }

    @Test
    fun `sendMessageIfSessionExist - missing session does not serialize or send`() {
        every { userSessionStateService.getByUserId(USER_ID_1) } returns null

        wsSender.sendMessageIfSessionExist(USER_ID_1, moveAcceptedMessage())

        verify(exactly = 1) { userSessionStateService.getByUserId(USER_ID_1) }
        verify { listOf(mapper, session, otherSession) wasNot Called }
        confirmVerified(userSessionStateService)
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false", "true, true")
    fun `saveSessionAndSendNewSessionMessage - registers session and sends supplied history and time`(
        hasMoves: Boolean, hasCreatedAt: Boolean
    ) {
        val moves = if (hasMoves) listOf(moveAcceptedMessage()) else emptyList()
        val createdAt = if (hasCreatedAt) CREATED_AT else null
        val expected = NewWsSession(
            messageType = MessageType.NEW_WS,
            backendUserId = USER_ID_1,
            sessionId = SESSION_ID,
            turns = moves,
            matchServerTimeMs = createdAt?.toEpochMilli()
        )
        every { mapper.writeValueAsString(expected) } returns SERIALIZED_SESSION_MESSAGE

        wsSender.saveSessionAndSendNewSessionMessage(USER_ID_1, moves, createdAt, session)

        verify(exactly = 1) {
            userSessionStateService.add(USER_ID_1, session)
            mapper.writeValueAsString(expected)
            session.sendMessage(match<TextMessage> { it.payload == SERIALIZED_SESSION_MESSAGE })
        }
        verifyOrder {
            userSessionStateService.add(USER_ID_1, session)
            mapper.writeValueAsString(expected)
            session.sendMessage(any())
        }
        verify { timeService wasNot Called }
        confirmVerified(userSessionStateService, mapper)
    }

    @ParameterizedTest
    @CsvSource("WHITE, true", "WHITE, false", "BLACK, true", "BLACK, false")
    fun `sendRoomCreatedMessage - sends personal room details to each player`(
        user1Side: SideType, hasUpdatedAt: Boolean
    ) {
        val user2Side = if (user1Side == SideType.WHITE) SideType.BLACK else SideType.WHITE
        val room = roomEntity(
            id = ROOM_ID, user1Side = user1Side, user2Side = user2Side,
            updatedAt = if (hasUpdatedAt) CREATED_AT else null
        )
        val expectedTime = (if (hasUpdatedAt) CREATED_AT else CURRENT_INSTANT).toEpochMilli()
        val first = RoomFoundMessageDto(
            backendUserId = USER_ID_1, room = ROOM_ID, opponentName = USERNAME_2,
            playerSide = user1Side, sessionId = SESSION_ID, matchServerTimeMs = expectedTime
        )
        val second = RoomFoundMessageDto(
            backendUserId = USER_ID_2, room = ROOM_ID, opponentName = USERNAME_1,
            playerSide = user2Side, sessionId = SESSION_ID_2, matchServerTimeMs = expectedTime
        )
        every { userSessionStateService.getByUserId(USER_ID_1) } returns session
        every { userSessionStateService.getByUserId(USER_ID_2) } returns otherSession
        if (!hasUpdatedAt) every { timeService.getNowInstant() } returns CURRENT_INSTANT
        every { mapper.writeValueAsString(first) } returns SERIALIZED_ROOM_MESSAGE_1
        every { mapper.writeValueAsString(second) } returns SERIALIZED_ROOM_MESSAGE_2

        wsSender.sendRoomCreatedMessage(room)

        verify(exactly = 1) {
            userSessionStateService.getByUserId(USER_ID_1)
            userSessionStateService.getByUserId(USER_ID_2)
            mapper.writeValueAsString(first)
            mapper.writeValueAsString(second)
            session.sendMessage(match<TextMessage> { it.payload == SERIALIZED_ROOM_MESSAGE_1 })
            otherSession.sendMessage(match<TextMessage> { it.payload == SERIALIZED_ROOM_MESSAGE_2 })
        }
        if (hasUpdatedAt) verify { timeService wasNot Called }
        else verify(exactly = 1) { timeService.getNowInstant() }
        confirmVerified(mapper, userSessionStateService)
    }

    @Test
    fun `sendRoomCreatedMessage - still notifies second player when first socket fails`() {
        val room = roomEntity(id = ROOM_ID, updatedAt = CURRENT_INSTANT)
        val first = RoomFoundMessageDto(
            backendUserId = USER_ID_1, room = ROOM_ID, opponentName = USERNAME_2,
            playerSide = SideType.WHITE, sessionId = SESSION_ID, matchServerTimeMs = CURRENT_INSTANT.toEpochMilli()
        )
        val second = RoomFoundMessageDto(
            backendUserId = USER_ID_2, room = ROOM_ID, opponentName = USERNAME_1,
            playerSide = SideType.BLACK, sessionId = SESSION_ID_2, matchServerTimeMs = CURRENT_INSTANT.toEpochMilli()
        )
        every { userSessionStateService.getByUserId(USER_ID_1) } returns session
        every { userSessionStateService.getByUserId(USER_ID_2) } returns otherSession
        every { mapper.writeValueAsString(first) } returns SERIALIZED_ROOM_MESSAGE_1
        every { mapper.writeValueAsString(second) } returns SERIALIZED_ROOM_MESSAGE_2
        every { session.sendMessage(any()) } throws IOException("Connection lost")

        wsSender.sendRoomCreatedMessage(room)

        verify(exactly = 1) {
            session.sendMessage(match<TextMessage> { it.payload == SERIALIZED_ROOM_MESSAGE_1 })
            otherSession.sendMessage(match<TextMessage> { it.payload == SERIALIZED_ROOM_MESSAGE_2 })
        }
        verifyOrder {
            session.sendMessage(any())
            otherSession.sendMessage(any())
        }
    }
}
