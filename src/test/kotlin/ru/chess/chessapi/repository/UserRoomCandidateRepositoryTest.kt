package ru.chess.chessapi.repository

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import ru.chess.chessapi.BaseSpringTestFull
import ru.chess.chessapi.TestData.Models.Entity.userEntity
import ru.chess.chessapi.TestData.Models.Entity.userRoomCandidateEntity
import ru.chess.chessapi.TestData.UserRoomCandidates.CREATED_AT
import ru.chess.chessapi.TestData.UserRoomCandidates.CURRENT_TIME
import ru.chess.chessapi.TestData.Users.AUTH_CODE_2
import ru.chess.chessapi.TestData.Users.SIGNATURE_2
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.TestData.Users.USERNAME_2
import ru.chess.chessapi.TestData.Users.USERNAME_3
import ru.chess.chessapi.TestData.Users.USERNAME_4
import ru.chess.chessapi.web.websocket.message.enums.SideType

class UserRoomCandidateRepositoryTest : BaseSpringTestFull() {

    @BeforeEach
    fun beforeEach() {
        userRepository.saveAll(
            listOf(
                userEntity(),
                userEntity(username = USERNAME_2, signature = SIGNATURE_2, authCode = AUTH_CODE_2),
            )
        )
    }

    @Test
    fun `findByUser - success`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val expected = userRoomCandidateRepository.save(userRoomCandidateEntity(user1, userSide = SideType.BLACK))
        userRoomCandidateRepository.save(userRoomCandidateEntity(user2))

        val actual = userRoomCandidateRepository.findByUser(user1)

        (actual?.id != null) shouldBe true
        actual?.id shouldBe expected.id
        actual?.user?.id shouldBe user1.id
        actual?.userSide shouldBe SideType.BLACK
        actual?.createdAt shouldBe CREATED_AT
        actual?.activeUntil shouldBe CURRENT_TIME
    }

    @Test
    fun `findByUser - candidate not found`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        userRoomCandidateRepository.save(userRoomCandidateEntity(user2))

        userRoomCandidateRepository.findByUser(user1) shouldBe null
    }

    @ParameterizedTest
    @ValueSource(longs = [-1, 0, 1])
    fun `findAllByActiveUntilIsGreaterThanEqual - time boundary`(seconds: Long) {
        val user = userRepository.findByUsername(USERNAME_1)!!
        val candidate = userRoomCandidateRepository.save(
            userRoomCandidateEntity(user, createdAt = CREATED_AT.plusSeconds(seconds))
        )

        val actual = userRoomCandidateRepository.findAllByActiveUntilIsGreaterThanEqual(
            CURRENT_TIME, PageRequest.of(0, 10)
        )

        actual.content.map { it.id } shouldBe if (seconds >= 0) listOf(candidate.id) else emptyList()
        actual.hasNext() shouldBe false
    }

    @Test
    fun `findAllByActiveUntilIsGreaterThanEqual - pagination`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val user3 = userRepository.save(userEntity(username = USERNAME_3, signature = null, authCode = null))
        val user4 = userRepository.save(userEntity(username = USERNAME_4, signature = null, authCode = null))
        val candidates = userRoomCandidateRepository.saveAll(
            listOf(
                userRoomCandidateEntity(user1),
                userRoomCandidateEntity(user2, createdAt = CREATED_AT.plusSeconds(1)),
                userRoomCandidateEntity(user3, createdAt = CREATED_AT.plusSeconds(2)),
            )
        )
        userRoomCandidateRepository.save(userRoomCandidateEntity(user4, createdAt = CREATED_AT.minusSeconds(1)))

        val first = userRoomCandidateRepository.findAllByActiveUntilIsGreaterThanEqual(
            CURRENT_TIME, PageRequest.of(0, 2, Sort.by("activeUntil"))
        )
        val second = userRoomCandidateRepository.findAllByActiveUntilIsGreaterThanEqual(
            CURRENT_TIME, first.nextPageable()
        )

        first.content.map { it.id } shouldBe candidates.take(2).map { it.id }
        first.hasNext() shouldBe true
        second.content.map { it.id } shouldBe listOf(candidates[2].id)
        second.hasNext() shouldBe false
    }

    @Test
    fun `findAllByActiveUntilIsGreaterThanEqual - empty database`() {
        val actual = userRoomCandidateRepository.findAllByActiveUntilIsGreaterThanEqual(
            CURRENT_TIME, PageRequest.of(0, 10)
        )

        actual.content shouldBe emptyList()
        actual.hasNext() shouldBe false
    }

    @Test
    fun `deleteAllByActiveUntilGreaterThan - deletes only after boundary`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val user3 = userRepository.save(userEntity(username = USERNAME_3, signature = null, authCode = null))
        val user4 = userRepository.save(userEntity(username = USERNAME_4, signature = null, authCode = null))
        val before = userRoomCandidateRepository.save(userRoomCandidateEntity(user1, createdAt = CREATED_AT.minusSeconds(1)))
        val equal = userRoomCandidateRepository.save(userRoomCandidateEntity(user2))
        userRoomCandidateRepository.saveAll(
            listOf(
                userRoomCandidateEntity(user3, createdAt = CREATED_AT.plusSeconds(1)),
                userRoomCandidateEntity(user4, createdAt = CREATED_AT.plusSeconds(2)),
            )
        )

        val actual = userRoomCandidateRepository.deleteAllByActiveUntilGreaterThan(CURRENT_TIME)

        actual shouldBe 2
        userRoomCandidateRepository.findAll().map { it.id } shouldContainExactlyInAnyOrder listOf(before.id, equal.id)
        userRepository.count() shouldBe 4L
    }

    @Test
    fun `deleteAllByActiveUntilGreaterThan - no matching candidates`() {
        val user = userRepository.findByUsername(USERNAME_1)!!
        val candidate = userRoomCandidateRepository.save(userRoomCandidateEntity(user))

        userRoomCandidateRepository.deleteAllByActiveUntilGreaterThan(CURRENT_TIME) shouldBe 0
        userRoomCandidateRepository.findAll().map { it.id } shouldBe listOf(candidate.id)
    }

    @Test
    fun `deleteByUser - deletes only requested user's candidate`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        userRoomCandidateRepository.save(userRoomCandidateEntity(user1))
        val remaining = userRoomCandidateRepository.save(userRoomCandidateEntity(user2))

        val actual = userRoomCandidateRepository.deleteByUser(user1)

        actual shouldBe 1
        userRoomCandidateRepository.findByUser(user1) shouldBe null
        userRoomCandidateRepository.findAll().map { it.id } shouldBe listOf(remaining.id)
        userRepository.count() shouldBe 2L
    }

    @Test
    fun `deleteByUser - candidate not found`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val remaining = userRoomCandidateRepository.save(userRoomCandidateEntity(user2))

        userRoomCandidateRepository.deleteByUser(user1) shouldBe 0
        userRoomCandidateRepository.findAll().map { it.id } shouldBe listOf(remaining.id)
    }
}
