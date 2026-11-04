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
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.TestData.Users.USERNAME_2
import ru.chess.chessapi.TestData.Users.SIGNATURE_1
import ru.chess.chessapi.TestData.Users.AUTH_CODE_1
import ru.chess.chessapi.TestData.Users.WHITE_BOT_NAME
import ru.chess.chessapi.TestData.Users.BLACK_BOT_NAME
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.repository.UserRepository
import java.util.Optional

@ExtendWith(MockKExtension::class)
class UserServiceTest {
    @InjectMockKs
    private lateinit var service: UserService
    @MockK(relaxUnitFun = true)
    private lateinit var userRepository: UserRepository

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = [" ", "\t"])
    fun `searchOrCreate - creates anonymous user for missing or blank signature`(signature: String?) {
        val expected = userEntity(id = USER_ID_1, signature = null)
        every { userRepository.save(any()) } returns expected

        service.searchOrCreateUserBySignatureAndId(signature, null, USERNAME_1) shouldBe expected

        verify(exactly = 1) { userRepository.save(userEntity(signature = null, authCode = null)) }
        confirmVerified(userRepository)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `searchOrCreate - existing id takes priority over signature`(hasSignature: Boolean) {
        val user = userEntity(id = USER_ID_1)
        every { userRepository.findById(USER_ID_1) } returns Optional.of(user)

        service.searchOrCreateUserBySignatureAndId(if (hasSignature) SIGNATURE_1 else null, USER_ID_1, USERNAME_2) shouldBe user

        verify(exactly = 1) { userRepository.findById(USER_ID_1) }
        confirmVerified(userRepository)
    }

    @Test
    fun `searchOrCreate - creates anonymous user when id not found`() {
        val user = userEntity(id = USER_ID_2, signature = null)
        every { userRepository.findById(USER_ID_1) } returns Optional.empty()
        every { userRepository.save(any()) } returns user

        service.searchOrCreateUserBySignatureAndId(null, USER_ID_1, USERNAME_1) shouldBe user

        verify { userRepository.save(userEntity(signature = null, authCode = null)) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `searchOrCreate - finds by signature with missing or unknown id`(hasId: Boolean) {
        val user = userEntity(id = USER_ID_2)
        if (hasId) every { userRepository.findById(USER_ID_1) } returns Optional.empty()
        every { userRepository.findBySignature(SIGNATURE_1) } returns user

        service.searchOrCreateUserBySignatureAndId(SIGNATURE_1, if (hasId) USER_ID_1 else null, USERNAME_1) shouldBe user

        verify(exactly = 1) { userRepository.findBySignature(SIGNATURE_1) }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `searchOrCreate - creates signed user when lookups fail`(hasId: Boolean) {
        val user = userEntity(id = USER_ID_2)
        if (hasId) every { userRepository.findById(USER_ID_1) } returns Optional.empty()
        every { userRepository.findBySignature(SIGNATURE_1) } returns null
        every { userRepository.save(any()) } returns user

        service.searchOrCreateUserBySignatureAndId(SIGNATURE_1, if (hasId) USER_ID_1 else null, USERNAME_1) shouldBe user

        verify(exactly = 1) { userRepository.save(userEntity(authCode = null)) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `lookups - returns repository results including missing user`(found: Boolean) {
        val expected = if (found) userEntity(id = USER_ID_1) else null
        every { userRepository.findById(USER_ID_1) } returns Optional.ofNullable(expected)
        every { userRepository.findByUsername(USERNAME_1) } returns expected
        every { userRepository.findBySignature(SIGNATURE_1) } returns expected
        every { userRepository.findByAuthCode(AUTH_CODE_1) } returns expected

        service.findById(USER_ID_1) shouldBe expected
        service.findUserByName(USERNAME_1) shouldBe expected
        service.findBySignature(SIGNATURE_1) shouldBe expected
        service.findByAuthCode(AUTH_CODE_1) shouldBe expected

        verify(exactly = 1) {
            userRepository.findById(USER_ID_1)
            userRepository.findByUsername(USERNAME_1)
            userRepository.findBySignature(SIGNATURE_1)
            userRepository.findByAuthCode(AUTH_CODE_1)
        }
    }

    @Test
    fun `save - returns persisted user`() {
        val user = userEntity()
        val saved = user.copy(id = USER_ID_1)
        every { userRepository.save(user) } returns saved

        service.save(user) shouldBe saved
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = [SIGNATURE_1])
    fun `createUser - saves requested name and signature with zero points`(signature: String?) {
        val expected = userEntity(signature = signature, authCode = null)
        every { userRepository.save(expected) } returns expected.copy(id = USER_ID_1)

        service.createUser(USERNAME_1, signature) shouldBe expected.copy(id = USER_ID_1)

        verify(exactly = 1) { userRepository.save(expected) }
    }

    @ParameterizedTest
    @CsvSource("true, false", "false, false", "true, true", "false, true")
    fun `findBotByColor - finds bot regardless of list order`(isWhite: Boolean, reverse: Boolean) {
        val white = userEntity(username = WHITE_BOT_NAME, isBot = true)
        val black = userEntity(username = BLACK_BOT_NAME, isBot = true)
        every { userRepository.findAllByBot(true) } returns if (reverse) listOf(black, white) else listOf(white, black)

        service.findBotByColor(isWhite) shouldBe if (isWhite) white else black
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 3])
    fun `findBotByColor - rejects invalid number of bots`(count: Int) {
        every { userRepository.findAllByBot(true) } returns List(count) { userEntity(isBot = true) }

        shouldThrow<Exception> { service.findBotByColor(true) }
        shouldThrow<Exception> { service.findBotByColor(false) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `findBotByColor - rejects missing bot of requested color`(isWhite: Boolean) {
        every { userRepository.findAllByBot(true) } returns listOf(userEntity(), userEntity(username = USERNAME_2))

        shouldThrow<Exception> { service.findBotByColor(isWhite) }
    }

    @Test
    fun `increaseTotalPointsToUsersPair - adds to current totals and saves both users`() {
        val first = userEntity(id = USER_ID_1, points = 100)
        val second = userEntity(id = USER_ID_2, points = 200)
        every { userRepository.saveAll(any<List<UserEntity>>()) } answers { firstArg() }

        service.increaseTotalPointsToUsersPair(first, 50, second, 20)

        first.totalPoints shouldBe 150L
        second.totalPoints shouldBe 220L
        verify(exactly = 1) { userRepository.saveAll(listOf(first, second)) }
    }
}
