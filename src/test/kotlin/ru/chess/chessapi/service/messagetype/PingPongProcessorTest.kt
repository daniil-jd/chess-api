package ru.chess.chessapi.service.messagetype

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
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Rooms.ROOM_ID_2
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.web.websocket.message.Ping
import ru.chess.chessapi.web.websocket.message.Pong
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.PingPongStatus
import ru.chess.chessapi.web.websocket.message.enums.SideType

@ExtendWith(MockKExtension::class)
class PingPongProcessorTest {
    @InjectMockKs
    private lateinit var processor: PingPongProcessor
    @MockK(relaxUnitFun = true)
    private lateinit var distributorService: DistributorService
    @MockK(relaxUnitFun = true)
    private lateinit var userSessionStateService: UserSessionStateService
    @MockK(relaxUnitFun = true)
    private lateinit var wsSender: WsSender
    @MockK(relaxUnitFun = true)
    private lateinit var session: WebSocketSession

    @ParameterizedTest
    @CsvSource(
        "WHITE, true, true, ONLINE, ONLINE", "WHITE, true, false, ONLINE, OFFLINE",
        "WHITE, false, true, OFFLINE, ONLINE", "WHITE, false, false, OFFLINE, OFFLINE",
        "BLACK, true, true, ONLINE, ONLINE", "BLACK, true, false, OFFLINE, ONLINE",
        "BLACK, false, true, ONLINE, OFFLINE", "BLACK, false, false, OFFLINE, OFFLINE"
    )
    fun `process - sends actual room and online status for each color`(
        user1Side: SideType, user1Online: Boolean, user2Online: Boolean,
        whiteStatus: PingPongStatus, blackStatus: PingPongStatus
    ) {
        val message = Ping(MessageType.PING, USER_ID_2, ROOM_ID_2)
        val room = roomEntity(
            id = ROOM_ID, user1Side = user1Side,
            user2Side = if (user1Side == SideType.WHITE) SideType.BLACK else SideType.WHITE
        )
        every { distributorService.findActiveRoomByUserId(USER_ID_2) } returns room
        every { userSessionStateService.getByUserId(USER_ID_1) } returns if (user1Online) session else null
        every { userSessionStateService.getByUserId(USER_ID_2) } returns if (user2Online) session else null
        val expected = Pong(MessageType.PONG, USER_ID_2, ROOM_ID, whiteStatus, blackStatus)

        processor.process(message, session)

        verify(exactly = 1) {
            distributorService.findActiveRoomByUserId(USER_ID_2)
            userSessionStateService.getByUserId(USER_ID_1)
            userSessionStateService.getByUserId(USER_ID_2)
            wsSender.sendMessageIfSessionExist(USER_ID_2, expected)
        }
        confirmVerified(distributorService, userSessionStateService, wsSender)
    }

    @Test
    fun `process - sends pong without room or statuses when no active room`() {
        val message = Ping(MessageType.PING, USER_ID_1, ROOM_ID)
        every { distributorService.findActiveRoomByUserId(USER_ID_1) } returns null
        every { userSessionStateService.getByUserId(null) } returns null
        val expected = Pong(MessageType.PONG, USER_ID_1, null, null, null)

        processor.process(message, session)

        verify(exactly = 1) {
            distributorService.findActiveRoomByUserId(USER_ID_1)
            wsSender.sendMessageIfSessionExist(USER_ID_1, expected)
        }
        verify(exactly = 2) { userSessionStateService.getByUserId(null) }
        confirmVerified(distributorService, userSessionStateService, wsSender)
    }
}
