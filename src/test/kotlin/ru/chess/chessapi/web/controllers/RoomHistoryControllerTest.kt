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
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import ru.chess.chessapi.TestData.Models.Request.roomHistorySaveRequest
import ru.chess.chessapi.TestData.Rooms.HISTORY
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Users.SIGNATURE_1
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.TestData.Users.USERNAME_2
import ru.chess.chessapi.exception.RoomDoesNotExistException
import ru.chess.chessapi.service.RoomHistorySaveService
import ru.chess.chessapi.service.RoomHistorySearchService
import ru.chess.chessapi.web.dto.response.HistoryRoomResponse
import ru.chess.chessapi.web.dto.response.RoomHistorySaveResponse
import ru.chess.chessapi.web.dto.response.RoomHistorySearchResponse
import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.SideType

@ExtendWith(MockKExtension::class)
class RoomHistoryControllerTest {
    @InjectMockKs
    private lateinit var controller: RoomHistoryController
    @MockK(relaxUnitFun = true)
    private lateinit var roomHistorySaveService: RoomHistorySaveService
    @MockK(relaxUnitFun = true)
    private lateinit var roomHistorySearchService: RoomHistorySearchService

    @Test
    fun `saveHistory - forwards request and returns saved room`() {
        val request = roomHistorySaveRequest()
        val expected = RoomHistorySaveResponse(USER_ID_1, ROOM_ID)
        every { roomHistorySaveService.saveHistory(request) } returns expected

        controller.saveHistory(request) shouldBe expected

        verify(exactly = 1) { roomHistorySaveService.saveHistory(request) }
        confirmVerified(roomHistorySaveService)
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false", "true, true")
    fun `getLatest20UserHistory - forwards nullable user id and signature`(hasId: Boolean, hasSignature: Boolean) {
        val id = if (hasId) USER_ID_1.toString() else null
        val signature = if (hasSignature) SIGNATURE_1 else null
        val expected = RoomHistorySearchResponse(USER_ID_1, SIGNATURE_1, 150, emptyList(), emptyList(), emptyList())
        every { roomHistorySearchService.getLatest30GamesEachType(id, signature) } returns expected

        controller.getLatest20UserHistory(id, signature) shouldBe expected

        verify(exactly = 1) { roomHistorySearchService.getLatest30GamesEachType(id, signature) }
        confirmVerified(roomHistorySearchService)
    }

    @Test
    fun `getHistoryRoomById - forwards room id and returns history`() {
        val expected = HistoryRoomResponse(ROOM_ID, USERNAME_1, USERNAME_2, HISTORY, FinishType.CHECKMATE, SideType.WHITE)
        every { roomHistorySearchService.getHistoryRoomById(ROOM_ID) } returns expected

        controller.getHistoryRoomById(ROOM_ID) shouldBe expected

        verify(exactly = 1) { roomHistorySearchService.getHistoryRoomById(ROOM_ID) }
        confirmVerified(roomHistorySearchService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["save", "search", "room"])
    fun `history methods - propagate service errors`(operation: String) {
        val failure = RoomDoesNotExistException(ROOM_ID)
        val request = roomHistorySaveRequest()
        when (operation) {
            "save" -> every { roomHistorySaveService.saveHistory(request) } throws failure
            "search" -> every { roomHistorySearchService.getLatest30GamesEachType(USER_ID_1.toString(), SIGNATURE_1) } throws failure
            else -> every { roomHistorySearchService.getHistoryRoomById(ROOM_ID) } throws failure
        }

        shouldThrow<RoomDoesNotExistException> {
            when (operation) {
                "save" -> controller.saveHistory(request)
                "search" -> controller.getLatest20UserHistory(USER_ID_1.toString(), SIGNATURE_1)
                else -> controller.getHistoryRoomById(ROOM_ID)
            }
        } shouldBe failure
    }
}
