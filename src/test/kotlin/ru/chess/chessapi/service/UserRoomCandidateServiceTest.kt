package ru.chess.chessapi.service

import io.kotest.matchers.shouldBe
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import ru.chess.chessapi.TestData.Models.Entity.userEntity
import ru.chess.chessapi.TestData.Models.Entity.userRoomCandidateEntity
import ru.chess.chessapi.TestData.UserRoomCandidates.CURRENT_TIME
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.entity.UserRoomCandidateEntity
import ru.chess.chessapi.repository.UserRoomCandidateRepository
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class UserRoomCandidateServiceTest {
    @InjectMockKs
    private lateinit var service: UserRoomCandidateService
    @MockK(relaxUnitFun = true)
    private lateinit var userRoomCandidateRepository: UserRoomCandidateRepository

    @BeforeEach
    fun beforeEach() {
        val now = CURRENT_TIME
        mockkStatic(LocalDateTime::class)
        every { LocalDateTime.now() } returns now
    }

    @AfterEach
    fun afterEach() {
        unmockkStatic(LocalDateTime::class)
    }

    @Test
    fun `createUserRoomCandidate - saves new candidate with requested side and current time`() {
        val user = userEntity(id = USER_ID_1)
        val expected = userRoomCandidateEntity(user, userSide = SideType.BLACK, createdAt = CURRENT_TIME)
        every { userRoomCandidateRepository.findByUser(user) } returns null
        every { userRoomCandidateRepository.save(expected) } returns expected

        service.createUserRoomCandidate(user, SideType.BLACK) shouldBe expected

        verify(exactly = 1) { userRoomCandidateRepository.save(expected) }
        verify(exactly = 0) { userRoomCandidateRepository.delete(any()) }
    }

    @Test
    fun `createUserRoomCandidate - replaces existing unexpired candidate`() {
        val user = userEntity(id = USER_ID_1)
        val existing = userRoomCandidateEntity(user, activeUntil = CURRENT_TIME.plusSeconds(1))
        val expected = userRoomCandidateEntity(user, userSide = SideType.BLACK, createdAt = CURRENT_TIME)
        every { userRoomCandidateRepository.findByUser(user) } returns existing
        every { userRoomCandidateRepository.save(expected) } returns expected

        service.createUserRoomCandidate(user, SideType.BLACK) shouldBe expected

        verifyOrder {
            userRoomCandidateRepository.delete(existing)
            userRoomCandidateRepository.save(expected)
        }
    }

    @ParameterizedTest
    @ValueSource(longs = [-1, 0])
    fun `createUserRoomCandidate - returns existing candidate at or after expiration`(seconds: Long) {
        val user = userEntity(id = USER_ID_1)
        val existing = userRoomCandidateEntity(user, activeUntil = CURRENT_TIME.plusSeconds(seconds))
        every { userRoomCandidateRepository.findByUser(user) } returns existing

        service.createUserRoomCandidate(user, SideType.BLACK) shouldBe existing

        verify(exactly = 1) { userRoomCandidateRepository.findByUser(user) }
        confirmVerified(userRoomCandidateRepository)
    }

    @Test
    fun `findActiveUserRoomCandidates - collects all slices using same cutoff`() {
        val user = userEntity(id = USER_ID_1)
        val candidates = (0..2).map { userRoomCandidateEntity(user, createdAt = CURRENT_TIME.plusSeconds(it.toLong())) }
        val firstPage = PageRequest.of(0, UserRoomCandidateService.BATCH_SIZE)
        val secondPage = firstPage.next()
        val thirdPage = secondPage.next()
        every { userRoomCandidateRepository.findAllByActiveUntilIsGreaterThanEqual(CURRENT_TIME, firstPage) } returns
            SliceImpl(listOf(candidates[0]), firstPage, true)
        every { userRoomCandidateRepository.findAllByActiveUntilIsGreaterThanEqual(CURRENT_TIME, secondPage) } returns
            SliceImpl(listOf(candidates[1]), secondPage, true)
        every { userRoomCandidateRepository.findAllByActiveUntilIsGreaterThanEqual(CURRENT_TIME, thirdPage) } returns
            SliceImpl(listOf(candidates[2]), thirdPage, false)

        service.findActiveUserRoomCandidates() shouldBe candidates

        verify(exactly = 1) {
            userRoomCandidateRepository.findAllByActiveUntilIsGreaterThanEqual(CURRENT_TIME, firstPage)
            userRoomCandidateRepository.findAllByActiveUntilIsGreaterThanEqual(CURRENT_TIME, secondPage)
            userRoomCandidateRepository.findAllByActiveUntilIsGreaterThanEqual(CURRENT_TIME, thirdPage)
        }
    }

    @Test
    fun `findActiveUserRoomCandidates - empty first slice stops pagination`() {
        val page = PageRequest.of(0, UserRoomCandidateService.BATCH_SIZE)
        every { userRoomCandidateRepository.findAllByActiveUntilIsGreaterThanEqual(CURRENT_TIME, page) } returns
            SliceImpl(emptyList<UserRoomCandidateEntity>(), page, false)

        service.findActiveUserRoomCandidates() shouldBe emptyList()

        verify(exactly = 1) { userRoomCandidateRepository.findAllByActiveUntilIsGreaterThanEqual(any(), any()) }
    }

    @Test
    fun `deleteCandidates - passes supplied candidates to repository`() {
        val candidates = listOf(userRoomCandidateEntity(userEntity(id = USER_ID_1)))

        service.deleteCandidates(candidates)

        verify(exactly = 1) { userRoomCandidateRepository.deleteAll(candidates) }
    }

    @Test
    fun `deleteAllOverdue - passes current time and returns deleted count`() {
        every { userRoomCandidateRepository.deleteAllByActiveUntilGreaterThan(CURRENT_TIME) } returns 3

        service.deleteAllOverdue() shouldBe 3
    }

    @Test
    fun `deleteByUser - returns deleted count`() {
        val user = userEntity(id = USER_ID_1)
        every { userRoomCandidateRepository.deleteByUser(user) } returns 1

        service.deleteByUser(user) shouldBe 1
    }
}
