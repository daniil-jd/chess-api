package ru.chess.chessapi.service

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
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Models.Entity.userEntity
import ru.chess.chessapi.TestData.Models.Entity.userRoomCandidateEntity
import ru.chess.chessapi.TestData.Models.Message.MOVE
import ru.chess.chessapi.TestData.Models.Message.requestForRoomMessage
import ru.chess.chessapi.TestData.Rooms.CURRENT_INSTANT
import ru.chess.chessapi.TestData.Rooms.DEADLINE_AT
import ru.chess.chessapi.TestData.Rooms.HISTORY
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Rooms.ROOM_ID_2
import ru.chess.chessapi.TestData.Users.AUTH_CODE_1
import ru.chess.chessapi.TestData.Users.AUTH_CODE_2
import ru.chess.chessapi.TestData.Users.SIGNATURE_1
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2
import ru.chess.chessapi.TestData.Users.USER_ID_3
import ru.chess.chessapi.TestData.Users.USER_ID_4
import ru.chess.chessapi.TestData.Users.USER_ID_5
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.TestData.Users.USERNAME_2
import ru.chess.chessapi.TestData.Users.USERNAME_3
import ru.chess.chessapi.TestData.Users.USERNAME_4
import ru.chess.chessapi.TestData.Users.USERNAME_5
import ru.chess.chessapi.entity.MatchmakingLockEntity
import ru.chess.chessapi.exception.ActiveRoomDoesNotExistByUserIdException
import ru.chess.chessapi.exception.UserDoesNotExistException
import ru.chess.chessapi.repository.MatchmakingLockRepository
import ru.chess.chessapi.web.dto.request.AuthBySignatureRequest
import ru.chess.chessapi.web.dto.request.UserChangeNameRequest
import ru.chess.chessapi.web.dto.response.AuthCodeResponse
import ru.chess.chessapi.web.dto.response.AuthResponse
import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.PromotionType
import ru.chess.chessapi.web.websocket.message.enums.RoomStatus
import ru.chess.chessapi.web.websocket.message.enums.SideType

@ExtendWith(MockKExtension::class)
class DistributorServiceTest {
    @InjectMockKs
    private lateinit var service: DistributorService
    @MockK(relaxUnitFun = true)
    private lateinit var userService: UserService
    @MockK(relaxUnitFun = true)
    private lateinit var userRoomCandidateService: UserRoomCandidateService
    @MockK(relaxUnitFun = true)
    private lateinit var roomService: RoomService
    @MockK(relaxUnitFun = true)
    private lateinit var authCodeService: AuthCodeService
    @MockK(relaxUnitFun = true)
    private lateinit var timeService: TimeService
    @MockK
    private lateinit var matchmakingLockRepository: MatchmakingLockRepository

    @BeforeEach
    fun prepareMatchmakingLock() {
        every { matchmakingLockRepository.lockForMatchmaking() } returns MatchmakingLockEntity()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `findRoomById - returns room service result`(found: Boolean) {
        val room = roomEntity(id = ROOM_ID).takeIf { found }
        every { roomService.findRoomById(ROOM_ID) } returns room

        service.findRoomById(ROOM_ID) shouldBe room

        verify(exactly = 1) { roomService.findRoomById(ROOM_ID) }
    }

    @ParameterizedTest
    @EnumSource(SideType::class)
    fun `createUserRoomCandidate - resolves user and creates candidate with requested side`(side: SideType) {
        val request = requestForRoomMessage().copy(side = side)
        val user = userEntity(id = USER_ID_2)
        val expected = userRoomCandidateEntity(user, userSide = side)
        every { userService.searchOrCreateUserBySignatureAndId(SIGNATURE_1, USER_ID_1, USERNAME_1) } returns user
        every { userRoomCandidateService.createUserRoomCandidate(user, side) } returns expected

        service.createUserRoomCandidate(request) shouldBe expected

        verify(exactly = 1) {
            userService.searchOrCreateUserBySignatureAndId(SIGNATURE_1, USER_ID_1, USERNAME_1)
            userRoomCandidateService.createUserRoomCandidate(user, side)
        }
        verifyOrder {
            matchmakingLockRepository.lockForMatchmaking()
            userService.searchOrCreateUserBySignatureAndId(SIGNATURE_1, USER_ID_1, USERNAME_1)
            userRoomCandidateService.createUserRoomCandidate(user, side)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1])
    fun `searchCandidatesAndCreateRooms - fewer than two candidates creates nothing`(count: Int) {
        val candidate = userRoomCandidateEntity(userEntity(id = USER_ID_1))
        every { userRoomCandidateService.findActiveUserRoomCandidates() } returns MutableList(count) { candidate }

        service.searchCandidatesAndCreateRooms() shouldBe emptyList()

        verify(exactly = 1) { userRoomCandidateService.findActiveUserRoomCandidates() }
        verifyOrder {
            matchmakingLockRepository.lockForMatchmaking()
            userRoomCandidateService.findActiveUserRoomCandidates()
        }
        confirmVerified(userRoomCandidateService)
        verify { roomService wasNot Called }
    }

    @ParameterizedTest
    @EnumSource(SideType::class, names = ["WHITE", "BLACK"])
    fun `searchCandidatesAndCreateRooms - same fixed colors remain unmatched`(side: SideType) {
        val first = userRoomCandidateEntity(userEntity(id = USER_ID_1), userSide = side)
        val second = userRoomCandidateEntity(userEntity(id = USER_ID_2), userSide = side)
        every { userRoomCandidateService.findActiveUserRoomCandidates() } returns mutableListOf(first, second)

        service.searchCandidatesAndCreateRooms() shouldBe emptyList()

        verify(exactly = 1) { userRoomCandidateService.findActiveUserRoomCandidates() }
        confirmVerified(userRoomCandidateService)
        verify { roomService wasNot Called }
    }

    @ParameterizedTest
    @CsvSource(
        "WHITE, BLACK", "BLACK, WHITE", "WHITE, RANDOM", "RANDOM, WHITE",
        "BLACK, RANDOM", "RANDOM, BLACK", "RANDOM, RANDOM"
    )
    fun `searchCandidatesAndCreateRooms - pairs compatible sides and removes matched candidates`(
        side1: SideType, side2: SideType
    ) {
        val first = userRoomCandidateEntity(userEntity(id = USER_ID_1), userSide = side1)
        val second = userRoomCandidateEntity(userEntity(id = USER_ID_2, username = USERNAME_2), userSide = side2)
        val room = roomEntity(id = ROOM_ID)
        every { userRoomCandidateService.findActiveUserRoomCandidates() } returns mutableListOf(first, second)
        every { roomService.createRoomForOnlineMatch(any(), any(), any(), any(), any(), any()) } returns room

        service.searchCandidatesAndCreateRooms() shouldBe listOf(room)

        verify(exactly = 1) {
            roomService.createRoomForOnlineMatch(any(), any(), any(), any(), any(), any())
            userRoomCandidateService.deleteCandidates(listOf(second, first))
        }
        verifyOrder {
            roomService.createRoomForOnlineMatch(any(), any(), any(), any(), any(), any())
            userRoomCandidateService.deleteCandidates(listOf(second, first))
        }
    }

    @Test
    fun `searchCandidatesAndCreateRooms - creates disjoint pairs and leaves unmatched candidate`() {
        val first = userRoomCandidateEntity(userEntity(id = USER_ID_1), userSide = SideType.WHITE)
        val second = userRoomCandidateEntity(userEntity(id = USER_ID_2, username = USERNAME_2), userSide = SideType.WHITE)
        val third = userRoomCandidateEntity(userEntity(id = USER_ID_3, username = USERNAME_3), userSide = SideType.BLACK)
        val fourth = userRoomCandidateEntity(userEntity(id = USER_ID_4, username = USERNAME_4), userSide = SideType.BLACK)
        val unmatched = userRoomCandidateEntity(userEntity(id = USER_ID_5, username = USERNAME_5), userSide = SideType.WHITE)
        val room1 = roomEntity(id = ROOM_ID, user1 = third.user, user2 = first.user, user1Side = SideType.BLACK, user2Side = SideType.WHITE)
        val room2 = roomEntity(id = ROOM_ID_2, user1 = fourth.user, user2 = second.user, user1Side = SideType.BLACK, user2Side = SideType.WHITE)
        every { userRoomCandidateService.findActiveUserRoomCandidates() } returns mutableListOf(first, second, third, fourth, unmatched)
        every {
            roomService.createRoomForOnlineMatch(third.user, SideType.BLACK, USERNAME_3, first.user, SideType.WHITE, USERNAME_1)
        } returns room1
        every {
            roomService.createRoomForOnlineMatch(fourth.user, SideType.BLACK, USERNAME_4, second.user, SideType.WHITE, USERNAME_2)
        } returns room2

        service.searchCandidatesAndCreateRooms() shouldBe listOf(room1, room2)

        verify(exactly = 1) {
            userRoomCandidateService.findActiveUserRoomCandidates()
            userRoomCandidateService.deleteCandidates(listOf(third, first))
            userRoomCandidateService.deleteCandidates(listOf(fourth, second))
        }
        verify(exactly = 2) { roomService.createRoomForOnlineMatch(any(), any(), any(), any(), any(), any()) }
        confirmVerified(userRoomCandidateService)
    }

    @Test
    fun `searchCandidatesAndCreateRooms - failed creation does not remove candidates`() {
        val first = userRoomCandidateEntity(userEntity(id = USER_ID_1), userSide = SideType.WHITE)
        val second = userRoomCandidateEntity(userEntity(id = USER_ID_2), userSide = SideType.BLACK)
        val failure = IllegalStateException("Cannot create room")
        every { userRoomCandidateService.findActiveUserRoomCandidates() } returns mutableListOf(first, second)
        every { roomService.createRoomForOnlineMatch(any(), any(), any(), any(), any(), any()) } throws failure

        shouldThrow<IllegalStateException> { service.searchCandidatesAndCreateRooms() } shouldBe failure

        verify(exactly = 0) { userRoomCandidateService.deleteCandidates(any()) }
    }

    @ParameterizedTest
    @CsvSource(
        "RANDOM, RANDOM, false, WHITE, BLACK", "RANDOM, BLACK, false, WHITE, BLACK",
        "RANDOM, WHITE, true, WHITE, BLACK", "WHITE, BLACK, false, WHITE, BLACK",
        "WHITE, RANDOM, false, WHITE, BLACK", "BLACK, WHITE, false, BLACK, WHITE",
        "BLACK, RANDOM, false, BLACK, WHITE"
    )
    fun `createRoomWithCandidates - resolves random sides and forwards players and names`(
        side1: SideType, side2: SideType, swap: Boolean, assigned1: SideType, assigned2: SideType
    ) {
        val user1 = userEntity(id = USER_ID_1)
        val user2 = userEntity(id = USER_ID_2, username = USERNAME_2)
        val first = if (swap) user2 else user1
        val second = if (swap) user1 else user2
        val expected = roomEntity(id = ROOM_ID, user1 = first, user2 = second, user1Side = assigned1, user2Side = assigned2)
        every {
            roomService.createRoomForOnlineMatch(first, assigned1, first.username, second, assigned2, second.username)
        } returns expected

        service.createRoomWithCandidates(user1, side1, user2, side2) shouldBe expected

        verify(exactly = 1) {
            roomService.createRoomForOnlineMatch(first, assigned1, first.username, second, assigned2, second.username)
        }
        confirmVerified(roomService)
    }

    @ParameterizedTest
    @EnumSource(SideType::class, names = ["WHITE", "BLACK"])
    fun `createRoomWithCandidates - rejects identical fixed colors`(side: SideType) {
        shouldThrow<Exception> { service.createRoomWithCandidates(userEntity(id = USER_ID_1), side, userEntity(id = USER_ID_2), side) }

        verify { roomService wasNot Called }
    }

    @Test
    fun `cancelRoomCandidate - deletes candidate of found user`() {
        val user = userEntity(id = USER_ID_1)
        every { userService.findById(USER_ID_1) } returns user
        every { userRoomCandidateService.deleteByUser(user) } returns 1

        service.cancelRoomCandidate(USER_ID_1)

        verify(exactly = 1) { userRoomCandidateService.deleteByUser(user) }
        verifyOrder {
            matchmakingLockRepository.lockForMatchmaking()
            userService.findById(USER_ID_1)
            userRoomCandidateService.deleteByUser(user)
        }
    }

    @Test
    fun `cancelRoomCandidate - missing user does not delete anything`() {
        every { userService.findById(USER_ID_1) } returns null

        service.cancelRoomCandidate(USER_ID_1)

        verify { userRoomCandidateService wasNot Called }
    }

    @ParameterizedTest
    @ValueSource(strings = ["create", "search", "cancel"])
    fun `missing matchmaking lock prevents queue operations`(operation: String) {
        every { matchmakingLockRepository.lockForMatchmaking() } returns null

        shouldThrow<IllegalStateException> {
            when (operation) {
                "create" -> service.createUserRoomCandidate(requestForRoomMessage())
                "search" -> service.searchCandidatesAndCreateRooms()
                "cancel" -> service.cancelRoomCandidate(USER_ID_1)
            }
        }.message shouldBe "Missing matchmaking_lock row with id=1"

        verify { listOf(userService, userRoomCandidateService, roomService) wasNot Called }
    }

    @Test
    fun `updateRoomHistory - initializes history and updates turn time and deadline`() {
        val room = roomEntity(id = ROOM_ID)
        val saved = room.copy(history = MOVE, lastMoveSide = SideType.BLACK, updatedAt = CURRENT_INSTANT, deadlineAt = DEADLINE_AT)
        every { roomService.findActiveRoomForUpdate(ROOM_ID) } returns room
        every { timeService.getRoomDeadLineInstant() } returns DEADLINE_AT
        every { timeService.getNowInstant() } returns CURRENT_INSTANT
        every { roomService.save(saved) } returns saved

        service.updateRoomHistory(ROOM_ID, MOVE, null, SideType.BLACK) shouldBe saved

        room shouldBe saved
        verify(exactly = 1) { roomService.save(saved) }
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(PromotionType::class)
    fun `updateRoomHistory - appends move with promotion suffix`(promotion: PromotionType?) {
        val room = roomEntity(id = ROOM_ID, history = HISTORY)
        val suffix = when (promotion) {
            PromotionType.QUEEN -> "Q"
            PromotionType.ROOK -> "R"
            PromotionType.BISHOP -> "B"
            PromotionType.KNIGHT -> "N"
            null -> ""
        }
        every { roomService.findActiveRoomForUpdate(ROOM_ID) } returns room
        every { timeService.getRoomDeadLineInstant() } returns DEADLINE_AT
        every { timeService.getNowInstant() } returns CURRENT_INSTANT
        every { roomService.save(room) } answers { firstArg() }

        val actual = service.updateRoomHistory(ROOM_ID, MOVE, promotion, SideType.WHITE)

        actual.history shouldBe "$HISTORY $MOVE$suffix"
        actual.lastMoveSide shouldBe SideType.WHITE
        actual.updatedAt shouldBe CURRENT_INSTANT
        actual.deadlineAt shouldBe DEADLINE_AT
        actual.roomStatus shouldBe RoomStatus.ACTIVE
        verify(exactly = 1) { roomService.save(room) }
    }

    @ParameterizedTest
    @CsvSource(
        "CHECKMATE, WHITE", "CHECKMATE, BLACK", "SURRENDER, WHITE", "SURRENDER, BLACK",
        "TIMEOUT, WHITE", "TIMEOUT, BLACK", "DRAW, WHITE", "DRAW, BLACK"
    )
    fun `updateRoomHistoryWhenMatchIsOver - appends result and finishes room`(finish: FinishType, side: SideType) {
        val room = roomEntity(id = ROOM_ID, history = HISTORY)
        every { roomService.findActiveRoomForUpdate(ROOM_ID) } returns room
        every { timeService.getRoomDeadLineInstant() } returns DEADLINE_AT
        every { timeService.getNowInstant() } returns CURRENT_INSTANT
        every { roomService.save(room) } answers { firstArg() }

        val actual = service.updateRoomHistoryWhenMatchIsOver(ROOM_ID, side, finish)

        actual.history shouldBe "$HISTORY $finish"
        actual.winnerSide shouldBe side
        actual.lastMoveSide shouldBe side
        actual.winType shouldBe finish
        actual.roomStatus shouldBe RoomStatus.FINISHED
        actual.updatedAt shouldBe CURRENT_INSTANT
        actual.deadlineAt shouldBe DEADLINE_AT
        verify(exactly = 1) { roomService.save(room) }
    }

    @Test
    fun `updateRoomHistoryWhenMatchIsOver - initializes empty history with finish type`() {
        val room = roomEntity(id = ROOM_ID)
        every { roomService.findActiveRoomForUpdate(ROOM_ID) } returns room
        every { timeService.getRoomDeadLineInstant() } returns DEADLINE_AT
        every { timeService.getNowInstant() } returns CURRENT_INSTANT
        every { roomService.save(room) } answers { firstArg() }

        service.updateRoomHistoryWhenMatchIsOver(ROOM_ID, SideType.WHITE, FinishType.SURRENDER).history shouldBe FinishType.SURRENDER.name
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `history updates - missing active room does not save or change time`(finish: Boolean) {
        every { roomService.findActiveRoomForUpdate(ROOM_ID) } returns null

        shouldThrow<ActiveRoomDoesNotExistByUserIdException> {
            if (finish) service.updateRoomHistoryWhenMatchIsOver(ROOM_ID, SideType.WHITE, FinishType.CHECKMATE)
            else service.updateRoomHistory(ROOM_ID, MOVE, null, SideType.WHITE)
        }

        verify(exactly = 0) { roomService.save(any()) }
        verify { timeService wasNot Called }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = [SIGNATURE_1])
    fun `authBySignature - searches without id and responds with resolved user`(signature: String?) {
        val user = userEntity(id = USER_ID_2, username = USERNAME_2)
        every { userService.searchOrCreateUserBySignatureAndId(signature, null, USERNAME_1) } returns user

        service.authBySignature(AuthBySignatureRequest(signature, USERNAME_1)) shouldBe AuthResponse(USER_ID_2, USERNAME_2)

        verify(exactly = 1) { userService.searchOrCreateUserBySignatureAndId(signature, null, USERNAME_1) }
    }

    @Test
    fun `generateAuthCode - replaces stored code and returns generated code`() {
        val user = userEntity(id = USER_ID_1, authCode = AUTH_CODE_1)
        every { userService.findById(USER_ID_1) } returns user
        every { authCodeService.generateShortAuthCode(USER_ID_1) } returns AUTH_CODE_2
        every { userService.save(user) } answers { firstArg() }

        service.generateAuthCode(USER_ID_1) shouldBe AuthCodeResponse(AUTH_CODE_2)

        user.authCode shouldBe AUTH_CODE_2
        verify(exactly = 1) { userService.save(match { it.id == USER_ID_1 && it.authCode == AUTH_CODE_2 }) }
        verifyOrder {
            userService.findById(USER_ID_1)
            authCodeService.generateShortAuthCode(USER_ID_1)
            userService.save(user)
        }
    }

    @Test
    fun `generateAuthCode - missing user does not generate or save code`() {
        every { userService.findById(USER_ID_1) } returns null

        shouldThrow<UserDoesNotExistException> { service.generateAuthCode(USER_ID_1) }

        verify { authCodeService wasNot Called }
        verify(exactly = 0) { userService.save(any()) }
    }

    @Test
    fun `validateAuthCode - responds with matched user`() {
        every { userService.findByAuthCode(AUTH_CODE_1) } returns userEntity(id = USER_ID_1)

        service.validateAuthCode(AUTH_CODE_1) shouldBe AuthResponse(USER_ID_1, USERNAME_1)

        verify(exactly = 1) { userService.findByAuthCode(AUTH_CODE_1) }
        confirmVerified(userService)
    }

    @Test
    fun `validateAuthCode - rejects unknown code`() {
        every { userService.findByAuthCode(AUTH_CODE_1) } returns null

        shouldThrow<UserDoesNotExistException> { service.validateAuthCode(AUTH_CODE_1) }
    }

    @Test
    fun `changeUserName - updates name and saves existing user`() {
        val user = userEntity(id = USER_ID_1)
        every { userService.findById(USER_ID_1) } returns user
        every { userService.save(user) } answers { firstArg() }

        service.changeUserName(UserChangeNameRequest(USER_ID_1, USERNAME_2))

        user.username shouldBe USERNAME_2
        verify(exactly = 1) { userService.save(match { it.id == USER_ID_1 && it.username == USERNAME_2 }) }
    }

    @Test
    fun `changeUserName - missing user is not saved`() {
        every { userService.findById(USER_ID_1) } returns null

        shouldThrow<UserDoesNotExistException> { service.changeUserName(UserChangeNameRequest(USER_ID_1, USERNAME_2)) }

        verify(exactly = 0) { userService.save(any()) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `findActiveRoomByUserId - returns room or null for existing user`(found: Boolean) {
        val user = userEntity(id = USER_ID_1)
        val expected = if (found) roomEntity(id = ROOM_ID) else null
        every { userService.findById(USER_ID_1) } returns user
        every { roomService.findActiveRoomByUserForUpdate(USER_ID_1) } returns expected

        service.findActiveRoomByUserId(USER_ID_1) shouldBe expected

        verify(exactly = 1) { roomService.findActiveRoomByUserForUpdate(USER_ID_1) }
    }

    @Test
    fun `findActiveRoomByUserId - missing user does not query rooms`() {
        every { userService.findById(USER_ID_1) } returns null

        shouldThrow<UserDoesNotExistException> { service.findActiveRoomByUserId(USER_ID_1) }

        verify { roomService wasNot Called }
    }
}
