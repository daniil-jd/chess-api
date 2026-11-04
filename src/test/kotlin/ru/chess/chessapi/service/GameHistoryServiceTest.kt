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
import ru.chess.chessapi.TestData.Models.Entity.gameHistory
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Rooms.ROOM_ID_2
import ru.chess.chessapi.TestData.Rooms.HISTORY
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2
import ru.chess.chessapi.entity.GameHistory
import ru.chess.chessapi.entity.GameHistoryId
import ru.chess.chessapi.exception.RoomDoesNotExistException
import ru.chess.chessapi.exception.UserNotInRoomException
import ru.chess.chessapi.model.GameStatsProjection
import ru.chess.chessapi.repository.GameHistoryRepository
import ru.chess.chessapi.web.dto.request.FavouriteRequest
import ru.chess.chessapi.web.dto.response.FavouriteRoomResponse
import ru.chess.chessapi.web.websocket.message.enums.*
import java.util.Optional
import java.util.stream.Stream

@ExtendWith(MockKExtension::class)
class GameHistoryServiceTest {
    @InjectMockKs
    private lateinit var service: GameHistoryService
    @MockK(relaxUnitFun = true)
    private lateinit var gameHistoryRepository: GameHistoryRepository
    @MockK(relaxUnitFun = true)
    private lateinit var roomService: RoomService
    @MockK(relaxUnitFun = true)
    private lateinit var userService: UserService

    @Test
    fun `saveGameHistoryByRoom - existing history avoids duplicate points and writes`() {
        val room = roomEntity(id = ROOM_ID)
        val history = gameHistory(room.user1, room)
        every { gameHistoryRepository.findById(history.id) } returns Optional.of(history)

        service.saveGameHistoryByRoom(room.user1, room, GameType.ONLINE) shouldBe listOf(history)

        verify(exactly = 1) { gameHistoryRepository.findById(history.id) }
        confirmVerified(gameHistoryRepository)
        verify { userService wasNot Called }
    }

    @ParameterizedTest
    @MethodSource("outcomes")
    fun `saveGameHistoryByRoom - saves both outcomes points and next match numbers`(
        gameType: GameType, status: UserGameStatus, firstBot: Boolean, secondBot: Boolean,
        firstPoints: Int, secondPoints: Int
    ) {
        val room = roomEntity(
            id = ROOM_ID, gameType = gameType, roomStatus = RoomStatus.FINISHED,
            user1 = userEntity(id = USER_ID_1, isBot = firstBot),
            user2 = userEntity(id = USER_ID_2, isBot = secondBot),
            winnerSide = when (status) { UserGameStatus.WIN -> SideType.WHITE; UserGameStatus.LOSE -> SideType.BLACK; else -> null }
        )
        val opposite = when (status) { UserGameStatus.WIN -> UserGameStatus.LOSE; UserGameStatus.LOSE -> UserGameStatus.WIN; else -> UserGameStatus.DRAW }
        every { gameHistoryRepository.findById(GameHistoryId(USER_ID_1, ROOM_ID)) } returns Optional.empty()
        every { gameHistoryRepository.maxMatchNumberForUser(USER_ID_1) } returns 4
        every { gameHistoryRepository.maxMatchNumberForUser(USER_ID_2) } returns 9
        every { gameHistoryRepository.saveAllAndFlush(any<List<GameHistory>>()) } answers { firstArg() }
        val expected = listOf(
            gameHistory(room.user1, room, matchNumber = 5, userGameStatus = status, points = firstPoints, createdAt = room.createdAt),
            gameHistory(room.user2, room, matchNumber = 10, userGameStatus = opposite, points = secondPoints, createdAt = room.createdAt)
        )

        service.saveGameHistoryByRoom(room.user1, room, gameType) shouldBe expected

        verify(exactly = 1) {
            userService.increaseTotalPointsToUsersPair(room.user1, firstPoints, room.user2, secondPoints)
            gameHistoryRepository.saveAllAndFlush(expected)
        }
    }

    @ParameterizedTest
    @CsvSource("false, false, CREATED", "false, true, DELETED", "true, false, CREATED", "true, true, DELETED")
    fun `markOrUnmarkFavourite - toggles favourite for either player`(
        secondPlayer: Boolean, favourite: Boolean, status: FavouriteRoomResponse.Status
    ) {
        val room = roomEntity(id = ROOM_ID)
        val user = if (secondPlayer) room.user2 else room.user1
        val history = gameHistory(user, room, favourite = favourite)
        every { roomService.findRoomById(ROOM_ID) } returns room
        every { gameHistoryRepository.findByUserAndRoom(user, room) } returns history
        every { gameHistoryRepository.save(any()) } answers { firstArg() }

        service.markOrUnmarkFavourite(FavouriteRequest(ROOM_ID, user.id!!)) shouldBe FavouriteRoomResponse(status)

        history.favourite shouldBe !favourite
        verify(exactly = 1) { gameHistoryRepository.save(history) }
    }

    @Test
    fun `markOrUnmarkFavourite - missing room`() {
        every { roomService.findRoomById(ROOM_ID) } returns null

        shouldThrow<RoomDoesNotExistException> { service.markOrUnmarkFavourite(FavouriteRequest(ROOM_ID, USER_ID_1)) }

        verify { gameHistoryRepository wasNot Called }
    }

    @Test
    fun `markOrUnmarkFavourite - user outside room`() {
        every { roomService.findRoomById(ROOM_ID) } returns roomEntity(id = ROOM_ID)

        shouldThrow<UserNotInRoomException> { service.markOrUnmarkFavourite(FavouriteRequest(ROOM_ID, ROOM_ID_2)) }

        verify { gameHistoryRepository wasNot Called }
    }

    @Test
    fun `findOrCreateIfNotExists - returns stored history`() {
        val room = roomEntity(id = ROOM_ID)
        val expected = gameHistory(room.user1, room)
        every { gameHistoryRepository.findByUserAndRoom(room.user1, room) } returns expected

        service.findOrCreateIfNotExists(room.user1, room) shouldBe expected

        verify(exactly = 1) { gameHistoryRepository.findByUserAndRoom(room.user1, room) }
        confirmVerified(gameHistoryRepository)
    }

    @Test
    fun `findOrCreateIfNotExists - creates both histories and returns requested player's history`() {
        val room = roomEntity(id = ROOM_ID, winnerSide = SideType.WHITE)
        every { gameHistoryRepository.findByUserAndRoom(room.user2, room) } returns null
        every { gameHistoryRepository.findById(GameHistoryId(USER_ID_2, ROOM_ID)) } returns Optional.empty()
        every { gameHistoryRepository.maxMatchNumberForUser(any()) } returns 0
        every { gameHistoryRepository.saveAllAndFlush(any<List<GameHistory>>()) } answers { firstArg() }

        val actual = service.findOrCreateIfNotExists(room.user2, room)

        actual.id shouldBe GameHistoryId(USER_ID_2, ROOM_ID)
        actual.userGameStatus shouldBe UserGameStatus.LOSE
        actual.points shouldBe 20
        verify(exactly = 1) { gameHistoryRepository.saveAllAndFlush(match<List<GameHistory>> { it.size == 2 }) }
    }

    @ParameterizedTest
    @EnumSource(GameType::class)
    fun `queries - forwards user id and game type`(gameType: GameType) {
        val room = roomEntity(id = ROOM_ID, gameType = gameType)
        val histories = listOf(gameHistory(room.user1, room))
        val stats = mockk<GameStatsProjection>()
        every { gameHistoryRepository.findLatest30RoomsByUserAndGameType(USER_ID_1, gameType.name) } returns histories
        every { gameHistoryRepository.getGameStatsNative(USER_ID_1, gameType.name) } returns stats
        every { gameHistoryRepository.findAllByUserAndFavourite(room.user1, true) } returns histories

        service.findLast30ByUserAndGameType(room.user1, gameType) shouldBe histories
        service.getGameStatsNative(room.user1, gameType) shouldBe stats
        service.findAllByUserAndFavourite(room.user1, true) shouldBe histories
    }

    @Test
    fun `queries - empty history and missing statistics`() {
        val user = userEntity(id = USER_ID_1)
        every { gameHistoryRepository.findLatest30RoomsByUserAndGameType(USER_ID_1, GameType.ONLINE.name) } returns emptyList()
        every { gameHistoryRepository.getGameStatsNative(USER_ID_1, GameType.ONLINE.name) } returns null
        every { gameHistoryRepository.findAllByUserAndFavourite(user, false) } returns emptyList()

        service.findLast30ByUserAndGameType(user, GameType.ONLINE) shouldBe emptyList()
        service.getGameStatsNative(user, GameType.ONLINE) shouldBe null
        service.findAllByUserAndFavourite(user, false) shouldBe emptyList()
    }

    @Test
    fun `save and saveAll - forwards histories and returns flushed result`() {
        val room = roomEntity(id = ROOM_ID)
        val history = gameHistory(room.user1, room)
        every { gameHistoryRepository.save(history) } returns history
        every { gameHistoryRepository.saveAllAndFlush(listOf(history)) } returns listOf(history)

        service.save(history)
        service.saveAll(listOf(history)) shouldBe listOf(history)

        verify(exactly = 1) { gameHistoryRepository.save(history) }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 9])
    fun `calculateNextMatchNumber - increments maximum`(maximum: Int) {
        every { gameHistoryRepository.maxMatchNumberForUser(USER_ID_1) } returns maximum

        service.calculateNextMatchNumber(userEntity(id = USER_ID_1)) shouldBe maximum + 1
    }

    companion object {
        @JvmStatic
        fun outcomes(): Stream<Arguments> = GameType.entries.flatMap { type ->
            val multiplier = when (type) { GameType.ONLINE, GameType.PSEUDO -> 2; GameType.BOT -> 1; else -> 0 }
            UserGameStatus.entries.flatMap { status ->
                listOf(false to false, true to false, false to true, true to true).map { (firstBot, secondBot) ->
                    val first = when (status) { UserGameStatus.WIN -> 25; UserGameStatus.LOSE -> 10; else -> 20 }
                    val second = when (status) { UserGameStatus.WIN -> 10; UserGameStatus.LOSE -> 25; else -> 20 }
                    Arguments.of(type, status, firstBot, secondBot, if (firstBot) 0 else first * multiplier, if (secondBot) 0 else second * multiplier)
                }
            }
        }.stream()
    }
}
