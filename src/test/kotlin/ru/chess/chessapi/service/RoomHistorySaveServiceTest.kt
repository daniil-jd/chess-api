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
import ru.chess.chessapi.TestData.Models.Entity.userEntity
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Models.Request.roomHistorySaveRequest
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Rooms.HISTORY
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.TestData.Users.SIGNATURE_1
import ru.chess.chessapi.exception.RoomDoesNotExistException
import ru.chess.chessapi.service.sessions.RoomAcceptedMessagesStateService
import ru.chess.chessapi.web.dto.response.RoomHistorySaveResponse
import ru.chess.chessapi.web.websocket.message.enums.*

@ExtendWith(MockKExtension::class)
class RoomHistorySaveServiceTest {
    @InjectMockKs
    private lateinit var service: RoomHistorySaveService
    @MockK(relaxUnitFun = true)
    private lateinit var roomService: RoomService
    @MockK(relaxUnitFun = true)
    private lateinit var userService: UserService
    @MockK(relaxUnitFun = true)
    private lateinit var gameHistoryService: GameHistoryService
    @MockK(relaxUnitFun = true)
    private lateinit var roomAcceptedMessagesStateService: RoomAcceptedMessagesStateService

    @ParameterizedTest
    @EnumSource(SideType::class, names = ["WHITE", "BLACK"])
    fun `saveHistory - updates existing room and saves history for requested side`(side: SideType) {
        val room = roomEntity(id = ROOM_ID)
        val request = roomHistorySaveRequest(userSide = side)
        val user = if (side == SideType.WHITE) room.user1 else room.user2
        every { roomService.findRoomById(ROOM_ID) } returns room
        every { roomService.save(room) } answers { firstArg() }
        every { gameHistoryService.saveGameHistoryByRoom(user, room, GameType.ONLINE) } returns emptyList()

        service.saveHistory(request) shouldBe RoomHistorySaveResponse(USER_ID_1, ROOM_ID)

        room.history shouldBe HISTORY
        room.winnerSide shouldBe SideType.WHITE
        room.winType shouldBe FinishType.CHECKMATE
        verifyOrder {
            roomService.save(room)
            gameHistoryService.saveGameHistoryByRoom(user, room, GameType.ONLINE)
            roomAcceptedMessagesStateService.clearByRoomId(room.id!!)
        }
        verify(exactly = 1) { roomAcceptedMessagesStateService.clearByRoomId(ROOM_ID) }
        verify { userService wasNot Called }
    }

    @Test
    fun `saveHistory - keeps accepted moves when game history save fails`() {
        val room = roomEntity(id = ROOM_ID)
        val failure = IllegalStateException("Cannot save game history")
        every { roomService.findRoomById(ROOM_ID) } returns room
        every { roomService.save(room) } returns room
        every { gameHistoryService.saveGameHistoryByRoom(room.user1, room, GameType.ONLINE) } throws failure

        shouldThrow<IllegalStateException> { service.saveHistory(roomHistorySaveRequest()) } shouldBe failure

        verify { roomAcceptedMessagesStateService wasNot Called }
    }

    @Test
    fun `saveHistory - preserves previously completed result`() {
        val room = roomEntity(id = ROOM_ID, winnerSide = SideType.BLACK, winType = FinishType.SURRENDER)
        every { roomService.findRoomById(ROOM_ID) } returns room
        every { roomService.save(room) } returns room
        every { gameHistoryService.saveGameHistoryByRoom(room.user1, room, GameType.ONLINE) } returns emptyList()

        service.saveHistory(roomHistorySaveRequest())

        room.history shouldBe HISTORY
        room.winnerSide shouldBe SideType.BLACK
        room.winType shouldBe FinishType.SURRENDER
    }

    @ParameterizedTest
    @CsvSource("true, false", "false, true")
    fun `saveHistory - fills result when either finish type or winner is missing`(hasWinner: Boolean, hasFinish: Boolean) {
        val room = roomEntity(
            id = ROOM_ID, winnerSide = if (hasWinner) SideType.BLACK else null,
            winType = if (hasFinish) FinishType.SURRENDER else null
        )
        every { roomService.findRoomById(ROOM_ID) } returns room
        every { roomService.save(room) } returns room
        every { gameHistoryService.saveGameHistoryByRoom(any(), room, GameType.ONLINE) } returns emptyList()

        service.saveHistory(roomHistorySaveRequest())

        room.winnerSide shouldBe SideType.WHITE
        room.winType shouldBe FinishType.CHECKMATE
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = [" "])
    fun `saveHistory - accepts draw without winner`(winner: String?) {
        val room = roomEntity(id = ROOM_ID)
        every { roomService.findRoomById(ROOM_ID) } returns room
        every { roomService.save(room) } returns room
        every { gameHistoryService.saveGameHistoryByRoom(any(), room, GameType.ONLINE) } returns emptyList()

        service.saveHistory(roomHistorySaveRequest(winnerSide = winner, finishType = FinishType.DRAW))

        room.winnerSide shouldBe null
        room.winType shouldBe FinishType.DRAW
    }

    @ParameterizedTest
    @EnumSource(SideType::class, names = ["WHITE", "BLACK"])
    fun `saveHistory - creates finished match with bot of opposite color`(side: SideType) {
        val request = roomHistorySaveRequest(room = null, userSide = side, opponentType = GameType.BOT)
        val user = userEntity(id = USER_ID_1)
        val bot = userEntity(id = USER_ID_2, isBot = true)
        val first = if (side == SideType.WHITE) user else bot
        val second = if (side == SideType.WHITE) bot else user
        val firstName = if (side == SideType.WHITE) request.username else request.opponentName
        val secondName = if (side == SideType.WHITE) request.opponentName else request.username
        val saved = roomEntity(id = ROOM_ID, user1 = first, user2 = second)
        every { userService.searchOrCreateUserBySignatureAndId(SIGNATURE_1, USER_ID_1, USERNAME_1) } returns user
        every { userService.findBotByColor(side == SideType.BLACK) } returns bot
        every {
            roomService.createRoomWithHistory(first, SideType.WHITE, firstName, second, SideType.BLACK, secondName,
                GameType.BOT, HISTORY, SideType.WHITE, FinishType.CHECKMATE)
        } returns saved
        every { gameHistoryService.saveGameHistoryByRoom(user, saved, GameType.BOT) } returns emptyList()

        service.saveHistory(request) shouldBe RoomHistorySaveResponse(USER_ID_1, ROOM_ID)

        verify(exactly = 1) { gameHistoryService.saveGameHistoryByRoom(user, saved, GameType.BOT) }
        verify { roomAcceptedMessagesStateService wasNot Called }
    }

    @Test
    fun `saveHistory - missing room does not save or clear states`() {
        every { roomService.findRoomById(ROOM_ID) } returns null

        shouldThrow<RoomDoesNotExistException> { service.saveHistory(roomHistorySaveRequest()) }

        verify(exactly = 0) { roomService.save(any()) }
        verify { listOf(userService, gameHistoryService, roomAcceptedMessagesStateService) wasNot Called }
    }

    @Test
    fun `saveHistory - invalid winner rejected before dependencies are called`() {
        shouldThrow<IllegalArgumentException> { service.saveHistory(roomHistorySaveRequest(winnerSide = "INVALID")) }

        verify { listOf(roomService, userService, gameHistoryService, roomAcceptedMessagesStateService) wasNot Called }
    }
}
