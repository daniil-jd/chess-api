package ru.chess.chessapi.service.messagetype

import io.kotest.assertions.throwables.shouldThrow
import io.mockk.Called
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Models.Message.moveAcceptedMessage
import ru.chess.chessapi.TestData.Models.Message.requestForWsRetryMessage
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Sessions.OLD_SESSION_ID
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.exception.ActiveRoomDoesNotExistByUserIdException
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.WsSender
import ru.chess.chessapi.service.sessions.RoomAcceptedMessagesStateService
import ru.chess.chessapi.service.sessions.SessionsAggregatorService
import ru.chess.chessapi.service.sessions.UserSessionStateService

@ExtendWith(MockKExtension::class)
class WsRetryProcessorTest {
    @InjectMockKs
    private lateinit var processor: WsRetryProcessor
    @MockK(relaxUnitFun = true)
    private lateinit var distributorService: DistributorService
    @MockK(relaxUnitFun = true)
    private lateinit var wsSender: WsSender
    @MockK(relaxUnitFun = true)
    private lateinit var userSessionStateService: UserSessionStateService
    @MockK(relaxUnitFun = true)
    private lateinit var roomAcceptedMessagesStateService: RoomAcceptedMessagesStateService
    @MockK(relaxUnitFun = true)
    private lateinit var sessionsAggregatorService: SessionsAggregatorService
    @MockK(relaxUnitFun = true)
    private lateinit var session: WebSocketSession
    @MockK(relaxUnitFun = true)
    private lateinit var oldSession: WebSocketSession

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `process - restores session and replays accepted moves`(hasOldSession: Boolean) {
        val message = requestForWsRetryMessage()
        val room = roomEntity(id = ROOM_ID)
        val moves = listOf(moveAcceptedMessage())
        every { userSessionStateService.getByUserId(USER_ID_1) } returns if (hasOldSession) oldSession else null
        every { oldSession.id } returns OLD_SESSION_ID
        every { distributorService.findActiveRoomByUserId(USER_ID_1) } returns room
        every { roomAcceptedMessagesStateService.getMessagesByRoomId(ROOM_ID) } returns moves

        processor.process(message, session)

        verify(exactly = 1) {
            userSessionStateService.getByUserId(USER_ID_1)
            distributorService.findActiveRoomByUserId(USER_ID_1)
            roomAcceptedMessagesStateService.getMessagesByRoomId(ROOM_ID)
            wsSender.saveSessionAndSendNewSessionMessage(USER_ID_1, moves, room.createdAt, session)
        }
        if (hasOldSession) {
            verify(exactly = 1) { sessionsAggregatorService.removeOldSessionFromStates(OLD_SESSION_ID) }
            verifyOrder {
                sessionsAggregatorService.removeOldSessionFromStates(OLD_SESSION_ID)
                distributorService.findActiveRoomByUserId(USER_ID_1)
                wsSender.saveSessionAndSendNewSessionMessage(USER_ID_1, moves, room.createdAt, session)
            }
        } else {
            verify { sessionsAggregatorService wasNot Called }
        }
        confirmVerified(distributorService, userSessionStateService, roomAcceptedMessagesStateService, wsSender, sessionsAggregatorService)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `process - restores session without accepted moves`(hasOldSession: Boolean) {
        val room = roomEntity(id = ROOM_ID)
        every { userSessionStateService.getByUserId(USER_ID_1) } returns if (hasOldSession) oldSession else null
        every { oldSession.id } returns OLD_SESSION_ID
        every { distributorService.findActiveRoomByUserId(USER_ID_1) } returns room
        every { roomAcceptedMessagesStateService.getMessagesByRoomId(ROOM_ID) } returns emptyList()

        processor.process(requestForWsRetryMessage(), session)

        verify(exactly = 1) { wsSender.saveSessionAndSendNewSessionMessage(USER_ID_1, emptyList(), room.createdAt, session) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `process - fails without active room and does not save new session`(hasOldSession: Boolean) {
        every { userSessionStateService.getByUserId(USER_ID_1) } returns if (hasOldSession) oldSession else null
        every { oldSession.id } returns OLD_SESSION_ID
        every { distributorService.findActiveRoomByUserId(USER_ID_1) } returns null

        shouldThrow<ActiveRoomDoesNotExistByUserIdException> {
            processor.process(requestForWsRetryMessage(), session)
        }

        verify(exactly = 1) {
            userSessionStateService.getByUserId(USER_ID_1)
            distributorService.findActiveRoomByUserId(USER_ID_1)
        }
        if (hasOldSession) {
            verify(exactly = 1) { sessionsAggregatorService.removeOldSessionFromStates(OLD_SESSION_ID) }
        } else {
            verify { sessionsAggregatorService wasNot Called }
        }
        verify { listOf(wsSender, roomAcceptedMessagesStateService) wasNot Called }
        confirmVerified(userSessionStateService, distributorService, sessionsAggregatorService)
    }
}
