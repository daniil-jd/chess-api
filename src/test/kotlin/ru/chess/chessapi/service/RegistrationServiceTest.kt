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
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import ru.chess.chessapi.TestData.Models.Entity.userEntity
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.TestData.Users.SIGNATURE_1
import ru.chess.chessapi.TestData.Users.SERIAL_NUMBER
import ru.chess.chessapi.exception.UserAlreadyExistException
import ru.chess.chessapi.web.dto.request.RegistrationRequest
import ru.chess.chessapi.web.dto.response.RegistrationResponse

@ExtendWith(MockKExtension::class)
class RegistrationServiceTest {
    @InjectMockKs
    private lateinit var service: RegistrationService
    @MockK(relaxUnitFun = true)
    private lateinit var userService: UserService

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = [SIGNATURE_1])
    fun `simpleRegister - saves new user and returns persisted fields`(signature: String?) {
        val newUser = userEntity(signature = signature, authCode = null)
        val saved = newUser.copy(id = USER_ID_1, serialNumber = SERIAL_NUMBER)
        every { userService.findUserByName(USERNAME_1) } returns null
        every { userService.save(newUser) } returns saved

        service.simpleRegister(RegistrationRequest(signature, USERNAME_1)) shouldBe
            RegistrationResponse(USER_ID_1.toString(), SERIAL_NUMBER, USERNAME_1, 0)

        verifyOrder {
            userService.findUserByName(USERNAME_1)
            userService.save(newUser)
        }
    }

    @Test
    fun `simpleRegister - rejects existing username without saving`() {
        every { userService.findUserByName(USERNAME_1) } returns userEntity(id = USER_ID_1)

        shouldThrow<UserAlreadyExistException> { service.simpleRegister(RegistrationRequest(SIGNATURE_1, USERNAME_1)) }

        verify(exactly = 0) { userService.save(any()) }
    }
}
