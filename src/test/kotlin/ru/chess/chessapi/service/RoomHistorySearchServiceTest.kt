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
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.TestData.Users.USERNAME_2
import ru.chess.chessapi.TestData.Users.SIGNATURE_1
import ru.chess.chessapi.exception.*
import ru.chess.chessapi.model.GameStatsProjection
import ru.chess.chessapi.web.dto.response.HistoryRoomResponse
import ru.chess.chessapi.web.dto.response.RoomHistorySearchResponse
import ru.chess.chessapi.web.websocket.message.enums.*

@ExtendWith(MockKExtension::class)
class RoomHistorySearchServiceTest {
    @InjectMockKs
    private lateinit var service: RoomHistorySearchService
    @MockK(relaxUnitFun = true)
    private lateinit var roomService: RoomService
    @MockK(relaxUnitFun = true)
    private lateinit var userService: UserService
    @MockK(relaxUnitFun = true)
    private lateinit var gameHistoryService: GameHistoryService

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `getLatest30GamesEachType - resolves by id or signature and builds all history sections`(byId: Boolean) {
        val user = userEntity(id = USER_ID_1, points = 150)
        val room = roomEntity(id = ROOM_ID, user1 = user, history = HISTORY, winType = FinishType.CHECKMATE, winnerSide = SideType.WHITE)
        val history = gameHistory(user, room, favourite = true)
        val unfinished = gameHistory(user, room.copy(id = ROOM_ID_2, winType = null))
        val stats = mockk<GameStatsProjection>()
        every { stats.gameType } returns GameType.ONLINE.name
        every { stats.wins } returns 3
        every { stats.losses } returns 2
        every { stats.draws } returns 1
        if (byId) every { userService.findById(USER_ID_1) } returns user
        else every { userService.findBySignature(SIGNATURE_1) } returns user
        GameType.entries.forEach { type ->
            every { gameHistoryService.findLast30ByUserAndGameType(user, type) } returns if (type == GameType.ONLINE) listOf(history, unfinished) else emptyList()
            every { gameHistoryService.getGameStatsNative(user, type) } returns if (type == GameType.ONLINE) stats else null
        }
        every { gameHistoryService.findAllByUserAndFavourite(user, true) } returns listOf(history)

        val actual = service.getLatest30GamesEachType(if (byId) USER_ID_1.toString() else " ", SIGNATURE_1)

        actual.backendUserId shouldBe USER_ID_1
        actual.signature shouldBe SIGNATURE_1
        actual.points shouldBe 150L
        actual.matchStatistics shouldBe listOf(RoomHistorySearchResponse.MatchStatistic(GameType.ONLINE, 3, 2, 1))
        actual.matchesHistory.size shouldBe 1
        val match = actual.matchesHistory.single()
        match.roomId shouldBe ROOM_ID
        match.userName shouldBe USERNAME_1
        match.opponentName shouldBe USERNAME_2
        match.userSide shouldBe SideType.WHITE
        match.history shouldBe HISTORY
        match.favourite shouldBe true
        match.finishType shouldBe FinishType.CHECKMATE
        actual.favouritesHistory shouldBe listOf(match)
        GameType.entries.forEach { type ->
            verify(exactly = 1) {
                gameHistoryService.findLast30ByUserAndGameType(user, type)
                gameHistoryService.getGameStatsNative(user, type)
            }
        }
        if (byId) verify(exactly = 0) { userService.findBySignature(any()) }
    }

    @Test
    fun `getLatest30GamesEachType - user without games gets empty sections`() {
        val user = userEntity(id = USER_ID_1)
        every { userService.findById(USER_ID_1) } returns user
        every { gameHistoryService.findLast30ByUserAndGameType(user, any()) } returns emptyList()
        every { gameHistoryService.getGameStatsNative(user, any()) } returns null
        every { gameHistoryService.findAllByUserAndFavourite(user, true) } returns emptyList()

        service.getLatest30GamesEachType(USER_ID_1.toString(), null) shouldBe
            RoomHistorySearchResponse(USER_ID_1, SIGNATURE_1, 0, emptyList(), emptyList(), emptyList())
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = [" "])
    fun `getLatest30GamesEachType - missing identifiers rejected`(identifier: String?) {
        shouldThrow<UserDoesNotExistException> { service.getLatest30GamesEachType(identifier, identifier) }

        verify { listOf(userService, gameHistoryService) wasNot Called }
    }

    @Test
    fun `getLatest30GamesEachType - invalid UUID does not fall back to signature`() {
        shouldThrow<UserDoesNotExistException> { service.getLatest30GamesEachType("invalid", SIGNATURE_1) }

        verify { listOf(userService, gameHistoryService) wasNot Called }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `getLatest30GamesEachType - missing user rejected`(byId: Boolean) {
        if (byId) every { userService.findById(USER_ID_1) } returns null
        else every { userService.findBySignature(SIGNATURE_1) } returns null

        shouldThrow<UserDoesNotExistException> {
            service.getLatest30GamesEachType(if (byId) USER_ID_1.toString() else null, SIGNATURE_1)
        }

        verify { gameHistoryService wasNot Called }
    }

    @Test
    fun `getHistoryRoomById - returns finished room details`() {
        every { roomService.findRoomById(ROOM_ID) } returns roomEntity(
            id = ROOM_ID, history = HISTORY, winnerSide = SideType.BLACK, winType = FinishType.CHECKMATE
        )

        service.getHistoryRoomById(ROOM_ID) shouldBe
            HistoryRoomResponse(ROOM_ID, USERNAME_1, USERNAME_2, HISTORY, FinishType.CHECKMATE, SideType.BLACK)
    }

    @Test
    fun `getHistoryRoomById - missing room`() {
        every { roomService.findRoomById(ROOM_ID) } returns null

        shouldThrow<RoomDoesNotExistException> { service.getHistoryRoomById(ROOM_ID) }
    }

    @Test
    fun `getHistoryRoomById - unfinished match`() {
        every { roomService.findRoomById(ROOM_ID) } returns roomEntity(id = ROOM_ID)

        shouldThrow<MatchIsNotOverException> { service.getHistoryRoomById(ROOM_ID) }
    }
}
