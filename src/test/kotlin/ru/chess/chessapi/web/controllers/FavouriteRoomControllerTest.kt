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
import org.junit.jupiter.params.provider.EnumSource
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.exception.RoomDoesNotExistException
import ru.chess.chessapi.service.GameHistoryService
import ru.chess.chessapi.web.dto.request.FavouriteRequest
import ru.chess.chessapi.web.dto.response.FavouriteRoomResponse

@ExtendWith(MockKExtension::class)
class FavouriteRoomControllerTest {
    @InjectMockKs
    private lateinit var controller: FavouriteRoomController
    @MockK(relaxUnitFun = true)
    private lateinit var gameHistoryService: GameHistoryService

    @ParameterizedTest
    @EnumSource(FavouriteRoomResponse.Status::class)
    fun `addOrDeleteFavourite - forwards request and returns service status`(status: FavouriteRoomResponse.Status) {
        val request = FavouriteRequest(ROOM_ID, USER_ID_1)
        val expected = FavouriteRoomResponse(status)
        every { gameHistoryService.markOrUnmarkFavourite(request) } returns expected

        controller.addOrDeleteFavourite(request) shouldBe expected

        verify(exactly = 1) { gameHistoryService.markOrUnmarkFavourite(request) }
        confirmVerified(gameHistoryService)
    }

    @Test
    fun `addOrDeleteFavourite - propagates missing room error`() {
        val request = FavouriteRequest(ROOM_ID, USER_ID_1)
        val failure = RoomDoesNotExistException(ROOM_ID)
        every { gameHistoryService.markOrUnmarkFavourite(request) } throws failure

        shouldThrow<RoomDoesNotExistException> { controller.addOrDeleteFavourite(request) } shouldBe failure
    }
}
