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
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USERNAME_2
import ru.chess.chessapi.exception.UserDoesNotExistException
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.web.dto.request.UserChangeNameRequest

@ExtendWith(MockKExtension::class)
class UserControllerTest {
    @InjectMockKs
    private lateinit var controller: UserController
    @MockK(relaxUnitFun = true)
    private lateinit var distributorService: DistributorService

    @Test
    fun `addOrDeleteFavourite - forwards name change request`() {
        val request = UserChangeNameRequest(USER_ID_1, USERNAME_2)

        controller.addOrDeleteFavourite(request)

        verify(exactly = 1) { distributorService.changeUserName(request) }
        confirmVerified(distributorService)
    }

    @Test
    fun `addOrDeleteFavourite - propagates missing user error`() {
        val request = UserChangeNameRequest(USER_ID_1, USERNAME_2)
        val failure = UserDoesNotExistException(USER_ID_1.toString())
        every { distributorService.changeUserName(request) } throws failure

        shouldThrow<UserDoesNotExistException> { controller.addOrDeleteFavourite(request) } shouldBe failure
    }
}
