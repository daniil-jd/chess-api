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
import org.junit.jupiter.params.provider.ValueSource
import ru.chess.chessapi.TestData.Users.AUTH_CODE_1
import ru.chess.chessapi.TestData.Users.SIGNATURE_1
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.exception.UserDoesNotExistException
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.web.dto.request.AuthBySignatureRequest
import ru.chess.chessapi.web.dto.response.AuthCodeResponse
import ru.chess.chessapi.web.dto.response.AuthResponse

@ExtendWith(MockKExtension::class)
class AuthenticationControllerTest {
    @InjectMockKs
    private lateinit var controller: AuthenticationController
    @MockK(relaxUnitFun = true)
    private lateinit var distributorService: DistributorService

    @Test
    fun `bySignature - forwards request and returns authentication result`() {
        val request = AuthBySignatureRequest(SIGNATURE_1, USERNAME_1)
        val expected = AuthResponse(USER_ID_1, USERNAME_1)
        every { distributorService.authBySignature(request) } returns expected

        controller.bySignature(request) shouldBe expected

        verify(exactly = 1) { distributorService.authBySignature(request) }
        confirmVerified(distributorService)
    }

    @Test
    fun `generate - forwards user id and returns code`() {
        val expected = AuthCodeResponse(AUTH_CODE_1)
        every { distributorService.generateAuthCode(USER_ID_1) } returns expected

        controller.generate(USER_ID_1) shouldBe expected

        verify(exactly = 1) { distributorService.generateAuthCode(USER_ID_1) }
        confirmVerified(distributorService)
    }

    @Test
    fun `authByCode - forwards code and returns authentication result`() {
        val expected = AuthResponse(USER_ID_1, USERNAME_1)
        every { distributorService.validateAuthCode(AUTH_CODE_1) } returns expected

        controller.authByCode(AUTH_CODE_1) shouldBe expected

        verify(exactly = 1) { distributorService.validateAuthCode(AUTH_CODE_1) }
        confirmVerified(distributorService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["signature", "generate", "code"])
    fun `authentication - propagates service error`(operation: String) {
        val failure = UserDoesNotExistException(USER_ID_1.toString())
        val request = AuthBySignatureRequest(SIGNATURE_1, USERNAME_1)
        when (operation) {
            "signature" -> every { distributorService.authBySignature(request) } throws failure
            "generate" -> every { distributorService.generateAuthCode(USER_ID_1) } throws failure
            else -> every { distributorService.validateAuthCode(AUTH_CODE_1) } throws failure
        }

        shouldThrow<UserDoesNotExistException> {
            when (operation) {
                "signature" -> controller.bySignature(request)
                "generate" -> controller.generate(USER_ID_1)
                else -> controller.authByCode(AUTH_CODE_1)
            }
        } shouldBe failure
    }
}
