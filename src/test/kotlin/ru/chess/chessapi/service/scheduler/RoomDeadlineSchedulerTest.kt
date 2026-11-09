package ru.chess.chessapi.service.scheduler

import io.mockk.Called
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import io.mockk.verify
import net.javacrumbs.shedlock.core.LockAssert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Models.Message.matchFinishedMessage
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2
import ru.chess.chessapi.service.RoomService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.service.sessions.RoomAcceptedMessagesStateService
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.SideType

@ExtendWith(MockKExtension::class)
class RoomDeadlineSchedulerTest {
    @InjectMockKs
    private lateinit var scheduler: RoomDeadlineScheduler
    @MockK
    private lateinit var roomService: RoomService
    @MockK(relaxUnitFun = true)
    private lateinit var wsSender: WsSender
    @MockK(relaxUnitFun = true)
    private lateinit var roomAcceptedMessagesStateService: RoomAcceptedMessagesStateService
    @MockK
    private lateinit var userSessionStateService: UserSessionStateService
    @MockK
    private lateinit var firstSession: WebSocketSession
    @MockK
    private lateinit var secondSession: WebSocketSession

    @ParameterizedTest
    @CsvSource(
        "WHITE, WHITE, true, true",
        "BLACK, BLACK, true, false",
        ", BLACK, false, true",
        ", BLACK, false, false"
    )
    fun `start - notifies connected players and clears accepted moves`(
        lastMove: SideType?, winner: SideType, firstConnected: Boolean, secondConnected: Boolean
    ) {
        val room = roomEntity(id = ROOM_ID, lastMoveSide = lastMove)
        val expected = matchFinishedMessage(winnerSide = winner, finishType = FinishType.TIMEOUT)
        every { roomService.updateStatusIfDeadlineAtExpired() } returns listOf(room)
        every { userSessionStateService.getByUserId(USER_ID_1) } returns firstSession.takeIf { firstConnected }
        every { userSessionStateService.getByUserId(USER_ID_2) } returns secondSession.takeIf { secondConnected }

        startWithLock()

        verify(exactly = if (firstConnected) 1 else 0) { wsSender.sendMessage(USER_ID_1, expected, firstSession) }
        verify(exactly = if (secondConnected) 1 else 0) { wsSender.sendMessage(USER_ID_2, expected, secondSession) }
        verify(exactly = 1) { roomAcceptedMessagesStateService.clearByRoomId(ROOM_ID) }
        confirmVerified(wsSender, roomAcceptedMessagesStateService)
    }

    @Test
    fun `start - no expired rooms means no notifications or cleanup`() {
        every { roomService.updateStatusIfDeadlineAtExpired() } returns emptyList()

        startWithLock()

        verify { listOf(wsSender, userSessionStateService, roomAcceptedMessagesStateService) wasNot Called }
    }

    private fun startWithLock() = mockkStatic(LockAssert::class) {
        every { LockAssert.assertLocked() } returns Unit
        scheduler.start()
        verify(exactly = 1) { LockAssert.assertLocked() }
    }
}
