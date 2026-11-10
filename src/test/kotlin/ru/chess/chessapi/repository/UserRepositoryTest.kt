package ru.chess.chessapi.repository

import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import ru.chess.chessapi.BaseSpringTestFull
import ru.chess.chessapi.TestData.Models.Entity.userEntity
import ru.chess.chessapi.TestData.Users.AUTH_CODE_2
import ru.chess.chessapi.TestData.Users.BOT_USERNAME_1
import ru.chess.chessapi.TestData.Users.BOT_USERNAME_2
import ru.chess.chessapi.TestData.Users.SIGNATURE_1
import ru.chess.chessapi.TestData.Users.SIGNATURE_2
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.TestData.Users.USERNAME_2

@Disabled("Не работает в гитхабе")
class UserRepositoryTest : BaseSpringTestFull() {

    @BeforeEach
    fun beforeEach() {
        userRepository.saveAll(
            listOf(
                userEntity(),
                userEntity(username = USERNAME_2, signature = SIGNATURE_2, authCode = AUTH_CODE_2),
                userEntity(username = BOT_USERNAME_1, signature = null, authCode = null, isBot = true),
                userEntity(username = BOT_USERNAME_2, signature = null, authCode = null, isBot = true),
            )
        )
    }

    @Test
    fun `findByUsername - success`() {
        val expectedSignature = SIGNATURE_1

        val actual = userRepository.findByUsername(USERNAME_1)

        (actual != null) shouldBe true
        (actual?.id != null) shouldBe true
        actual?.signature shouldBe expectedSignature
    }

    @Test
    fun `findByUsername - user not found`() {
        userRepository.findByUsername("unknownUsername") shouldBe null
    }

    @Test
    fun `findBySignature - success`() {
        val actual = userRepository.findBySignature(SIGNATURE_2)

        (actual != null) shouldBe true
        (actual?.id != null) shouldBe true
        actual?.username shouldBe USERNAME_2
        actual?.signature shouldBe SIGNATURE_2
    }

    @Test
    fun `findBySignature - user not found`() {
        userRepository.findBySignature("unknownSignature") shouldBe null
    }

    @Test
    fun `findByAuthCode - success`() {
        val actual = userRepository.findByAuthCode(AUTH_CODE_2)

        (actual != null) shouldBe true
        (actual?.id != null) shouldBe true
        actual?.username shouldBe USERNAME_2
        actual?.authCode shouldBe AUTH_CODE_2
    }

    @Test
    fun `findByAuthCode - user not found`() {
        userRepository.findByAuthCode("unknownAuthCode") shouldBe null
    }

    @Test
    fun `findAllByBot - returns bots`() {
        val actual = userRepository.findAllByBot(true)

        actual.map { it.username } shouldContainExactlyInAnyOrder listOf(BOT_USERNAME_1, BOT_USERNAME_2)
        actual.all { it.isBot } shouldBe true
    }

    @Test
    fun `findAllByBot - returns non bots`() {
        val actual = userRepository.findAllByBot(false)

        actual.map { it.username } shouldContainExactlyInAnyOrder listOf(USERNAME_1, USERNAME_2)
        actual.all { !it.isBot } shouldBe true
    }

    @Test
    fun `findAllByBot - no bots found`() {
        userRepository.deleteAll(userRepository.findAllByBot(true))

        userRepository.findAllByBot(true) shouldBe emptyList()
    }

}
