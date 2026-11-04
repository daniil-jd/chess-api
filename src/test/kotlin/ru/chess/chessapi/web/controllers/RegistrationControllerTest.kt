package ru.chess.chessapi.web.controllers

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import ru.chess.chessapi.TestData.Users.SERIAL_NUMBER
import ru.chess.chessapi.TestData.Users.SIGNATURE_1
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.exception.UserAlreadyExistException
import ru.chess.chessapi.service.RegistrationService
import ru.chess.chessapi.web.dto.request.RegistrationRequest
import ru.chess.chessapi.web.dto.response.RegistrationResponse

@ExtendWith(MockKExtension::class)
class RegistrationControllerTest {
    @InjectMockKs
    private lateinit var controller: RegistrationController
    @MockK(relaxUnitFun = true)
    private lateinit var service: RegistrationService

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = [SIGNATURE_1])
    fun `simpleRegistration - forwards optional signature and returns registered user`(signature: String?) {
        val request = RegistrationRequest(signature, USERNAME_1)
        val expected = RegistrationResponse(USER_ID_1.toString(), SERIAL_NUMBER, USERNAME_1, 50)
        every { service.simpleRegister(request) } returns expected

        controller.simpleRegistration(request) shouldBe expected

        verify(exactly = 1) { service.simpleRegister(request) }
        confirmVerified(service)
    }

    @Test
    fun `simpleRegistration - propagates duplicate username error`() {
        val request = RegistrationRequest(SIGNATURE_1, USERNAME_1)
        val failure = UserAlreadyExistException(USERNAME_1)
        every { service.simpleRegister(request) } throws failure

        shouldThrow<UserAlreadyExistException> { controller.simpleRegistration(request) } shouldBe failure
    }
}
