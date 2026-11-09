package ru.chess.chessapi.service.sessions

import io.kotest.matchers.shouldBe
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Models.Message.moveAcceptedMessage
import ru.chess.chessapi.TestData.Models.Message.MOVE_ID
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Rooms.ROOM_ID_2
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2

@ExtendWith(MockKExtension::class)
class RoomAcceptedMessagesStateServiceTest {
    @InjectMockKs
    private lateinit var service: RoomAcceptedMessagesStateService

    @Test
    fun `getMessagesByRoomId - unknown room`() {
        service.getMessagesByRoomId(ROOM_ID) shouldBe emptyList()
    }

    @Test
    fun `getMessageByRoomIdAndMoveId - finds move only in requested room`() {
        val message = moveAcceptedMessage()
        val other = message.copy(room = ROOM_ID_2)
        service.addMessage(ROOM_ID, message)
        service.addMessage(ROOM_ID_2, other)

        service.getMessageByRoomIdAndMoveId(ROOM_ID, MOVE_ID) shouldBe message
        service.getMessageByRoomIdAndMoveId(ROOM_ID_2, MOVE_ID) shouldBe other
        service.getMessageByRoomIdAndMoveId(ROOM_ID, ROOM_ID_2) shouldBe null

        service.clearByRoomId(ROOM_ID)

        service.getMessageByRoomIdAndMoveId(ROOM_ID, MOVE_ID) shouldBe null
    }

    @Test
    fun `getLastMoveByRoom - returns highest ply regardless of insertion order`() {
        val message = moveAcceptedMessage()
        service.getLastMoveByRoom(ROOM_ID) shouldBe null

        service.addMessage(ROOM_ID, message)
        service.addMessage(ROOM_ID, message.copy(ply = message.ply - 1))
        service.addMessage(ROOM_ID_2, message.copy(room = ROOM_ID_2, ply = message.ply + 1))

        service.getLastMoveByRoom(ROOM_ID) shouldBe message.ply
        service.clearByRoomId(ROOM_ID)
        service.getLastMoveByRoom(ROOM_ID) shouldBe null
    }

    @Test
    fun `addMessage - keeps order and separates rooms`() {
        val first = moveAcceptedMessage()
        val second = first.copy(ply = first.ply + 1)
        val other = first.copy(room = ROOM_ID_2)

        service.addMessage(ROOM_ID, first)
        service.addMessage(ROOM_ID_2, other)
        service.addMessage(ROOM_ID, second)

        service.getMessagesByRoomId(ROOM_ID) shouldBe listOf(first, second)
        service.getMessagesByRoomId(ROOM_ID_2) shouldBe listOf(other)
    }

    @Test
    fun `getMessagesByRoomId - returns snapshot unaffected by later changes`() {
        val first = moveAcceptedMessage()
        val second = first.copy(ply = first.ply + 1)
        service.addMessage(ROOM_ID, first)
        val snapshot = service.getMessagesByRoomId(ROOM_ID)

        service.addMessage(ROOM_ID, second)
        service.clearByRoomId(ROOM_ID)

        snapshot shouldBe listOf(first)
        service.getMessagesByRoomId(ROOM_ID) shouldBe emptyList()
    }

    @Test
    fun `clearByUserid - clears only requested room and allows new messages`() {
        val message = moveAcceptedMessage()
        val other = message.copy(room = ROOM_ID_2)
        service.addMessage(ROOM_ID, message)
        service.addMessage(ROOM_ID_2, other)

        service.clearByRoomId(ROOM_ID)

        service.getMessagesByRoomId(ROOM_ID) shouldBe emptyList()
        service.getMessagesByRoomId(ROOM_ID_2) shouldBe listOf(other)

        service.addMessage(ROOM_ID, message)

        service.getMessagesByRoomId(ROOM_ID) shouldBe listOf(message)
    }

    @Test
    fun `clearByUserid - unknown room does not affect stored messages`() {
        val message = moveAcceptedMessage()
        service.addMessage(ROOM_ID, message)

        service.clearByRoomId(ROOM_ID_2)

        service.getMessagesByRoomId(ROOM_ID) shouldBe listOf(message)
    }

    @Test
    fun `clearForBothRoomUsers - clears room messages without removing other rooms`() {
        val room = roomEntity(id = ROOM_ID)
        val message = moveAcceptedMessage()
        val other = message.copy(room = ROOM_ID_2)
        val userIdRoom1 = message.copy(room = USER_ID_1)
        val userIdRoom2 = message.copy(room = USER_ID_2)
        service.addMessage(ROOM_ID, message)
        service.addMessage(ROOM_ID_2, other)
        service.addMessage(USER_ID_1, userIdRoom1)
        service.addMessage(USER_ID_2, userIdRoom2)

        service.clearByRoomId(room.id!!)

        service.getMessagesByRoomId(ROOM_ID) shouldBe emptyList()
        service.getMessagesByRoomId(ROOM_ID_2) shouldBe listOf(other)
        service.getMessagesByRoomId(USER_ID_1) shouldBe listOf(userIdRoom1)
        service.getMessagesByRoomId(USER_ID_2) shouldBe listOf(userIdRoom2)
    }

    @Test
    fun `clearForBothRoomUsers - repeated cleanup is safe`() {
        val room = roomEntity(id = ROOM_ID)
        service.addMessage(ROOM_ID, moveAcceptedMessage())

        service.clearByRoomId(room.id!!)
        service.clearByRoomId(room.id!!)

        service.getMessagesByRoomId(ROOM_ID) shouldBe emptyList()
    }
}
