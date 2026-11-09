package ru.chess.chessapi.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.*
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Rooms.ROOM_ID_2
import ru.chess.chessapi.TestData.Rooms.CURRENT_INSTANT
import ru.chess.chessapi.TestData.Rooms.DEADLINE_AT
import ru.chess.chessapi.TestData.Rooms.HISTORY
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.TestData.Users.USERNAME_2
import ru.chess.chessapi.exception.ActiveRoomAlreadyExistException
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.repository.RoomRepository
import ru.chess.chessapi.web.websocket.message.enums.*
import java.util.Optional

@ExtendWith(MockKExtension::class)
class RoomServiceTest {
    @InjectMockKs
    private lateinit var service: RoomService
    @MockK(relaxUnitFun = true)
    private lateinit var roomRepository: RoomRepository
    @MockK(relaxUnitFun = true)
    private lateinit var timeService: TimeService

    @Test
    fun `updateStatusIfDeadlineAtExpired - saves timeouts and returns expired rooms`() {
        val roomWithMove = roomEntity(id = ROOM_ID, lastMoveSide = SideType.WHITE)
        val roomWithoutMoves = roomEntity(id = ROOM_ID_2)
        val rooms = listOf(roomWithMove, roomWithoutMoves)
        every { timeService.getNowInstant() } returns CURRENT_INSTANT
        every { roomRepository.findByExpiredDeadline(CURRENT_INSTANT) } returns rooms
        every { roomRepository.saveAll(rooms) } returns rooms

        service.updateStatusIfDeadlineAtExpired() shouldBe rooms

        roomWithMove.winnerSide shouldBe SideType.WHITE
        roomWithoutMoves.winnerSide shouldBe SideType.BLACK
        rooms.forEach {
            it.winType shouldBe FinishType.TIMEOUT
            it.roomStatus shouldBe RoomStatus.EXPIRED
            it.updatedAt shouldBe CURRENT_INSTANT
        }
        verify(exactly = 1) { roomRepository.saveAll(rooms) }
    }

    @Test
    fun `updateStatusIfDeadlineAtExpired - returns empty list when no room expired`() {
        every { timeService.getNowInstant() } returns CURRENT_INSTANT
        every { roomRepository.findByExpiredDeadline(CURRENT_INSTANT) } returns emptyList()
        every { roomRepository.saveAll(emptyList<RoomEntity>()) } returns emptyList()

        service.updateStatusIfDeadlineAtExpired() shouldBe emptyList()
    }

    @Test
    fun `createRoomForOnlineMatch - saves active room with requested players and times`() {
        val source = roomEntity()
        val expected = source.copy(createdAt = CURRENT_INSTANT, updatedAt = CURRENT_INSTANT, deadlineAt = DEADLINE_AT)
        val saved = expected.copy(id = ROOM_ID)
        every { roomRepository.findAllByUser1AndUser2ForUpdate(USER_ID_1, USER_ID_2) } returns emptyList()
        every { timeService.getNowInstant() } returns CURRENT_INSTANT
        every { timeService.getRoomDeadLineInstant() } returns DEADLINE_AT
        every { roomRepository.saveAndFlush(expected) } returns saved

        service.createRoomForOnlineMatch(source.user1, source.user1Side, source.user1Name, source.user2, source.user2Side, source.user2Name) shouldBe saved

        verify(exactly = 1) { roomRepository.saveAndFlush(expected) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `creation - rejects existing active room before saving`(withHistory: Boolean) {
        val room = roomEntity(id = ROOM_ID)
        every { roomRepository.findAllByUser1AndUser2ForUpdate(USER_ID_1, USER_ID_2) } returns listOf(room)

        shouldThrow<ActiveRoomAlreadyExistException> {
            if (withHistory) service.createRoomWithHistory(
                room.user1, SideType.WHITE, USERNAME_1, room.user2, SideType.BLACK, USERNAME_2,
                GameType.BOT, HISTORY, SideType.WHITE, FinishType.CHECKMATE
            ) else service.createRoomForOnlineMatch(room.user1, SideType.WHITE, USERNAME_1, room.user2, SideType.BLACK, USERNAME_2)
        }

        verify(exactly = 0) { roomRepository.save(any()) }
        verify(exactly = 0) { roomRepository.saveAndFlush(any()) }
        verify { timeService wasNot Called }
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(SideType::class, names = ["WHITE", "BLACK"])
    fun `createRoomWithHistory - persists finished room including optional winner`(winner: SideType?) {
        val source = roomEntity()
        val expected = roomEntity(
            gameType = GameType.BOT, roomStatus = RoomStatus.FINISHED, createdAt = CURRENT_INSTANT,
            updatedAt = CURRENT_INSTANT, history = HISTORY, winnerSide = winner,
            winType = FinishType.DRAW, lastMoveSide = winner
        )
        every { roomRepository.findAllByUser1AndUser2ForUpdate(USER_ID_1, USER_ID_2) } returns emptyList()
        every { timeService.getNowInstant() } returns CURRENT_INSTANT
        every { timeService.getRoomDeadLineInstant() } returns DEADLINE_AT
        every { roomRepository.save(expected) } returns expected.copy(id = ROOM_ID)

        service.createRoomWithHistory(
            source.user1, SideType.WHITE, USERNAME_1, source.user2, SideType.BLACK, USERNAME_2,
            GameType.BOT, HISTORY, winner, FinishType.DRAW
        ) shouldBe expected.copy(id = ROOM_ID)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `lookups - returns matching room or null using current time`(found: Boolean) {
        val room = if (found) roomEntity(id = ROOM_ID) else null
        every { timeService.getNowInstant() } returns CURRENT_INSTANT
        every { roomRepository.findById(ROOM_ID) } returns Optional.ofNullable(room)
        every { roomRepository.findActiveByUserForUpdate(USER_ID_1, CURRENT_INSTANT) } returns room
        every { roomRepository.findActiveForUpdate(ROOM_ID, CURRENT_INSTANT) } returns room

        service.findRoomById(ROOM_ID) shouldBe room
        service.findActiveRoomByUserForUpdate(USER_ID_1) shouldBe room
        service.findActiveRoomForUpdate(ROOM_ID) shouldBe room
    }

    @Test
    fun `save - returns persisted room`() {
        val room = roomEntity()
        val saved = room.copy(id = ROOM_ID)
        every { roomRepository.save(room) } returns saved

        service.save(room) shouldBe saved
    }
}
