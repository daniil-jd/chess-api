package ru.chess.chessapi.service.messagetype

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Called
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Models.Entity.userEntity
import ru.chess.chessapi.TestData.Models.Entity.userRoomCandidateEntity
import ru.chess.chessapi.TestData.Models.Message.requestForRoomMessage
import ru.chess.chessapi.TestData.Rooms.ROOM_ID
import ru.chess.chessapi.TestData.Rooms.ROOM_ID_2
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.service.WsSender

@ExtendWith(MockKExtension::class)
class RequestForRoomProcessorTest {
    @InjectMockKs
    private lateinit var processor: RequestForRoomProcessor
    @MockK(relaxUnitFun = true)
    private lateinit var distributorService: DistributorService
    @MockK(relaxUnitFun = true)
    private lateinit var wsSender: WsSender
    @MockK(relaxUnitFun = true)
    private lateinit var session: WebSocketSession

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun `process - registers session before matchmaking and sends every created room`(roomCount: Int) {
        val message = requestForRoomMessage()
        val candidate = userRoomCandidateEntity(userEntity(id = USER_ID_1))
        val rooms = listOf(roomEntity(id = ROOM_ID), roomEntity(id = ROOM_ID_2)).take(roomCount)
        every { distributorService.createUserRoomCandidate(message) } returns candidate
        every { distributorService.searchCandidatesAndCreateRooms() } returns rooms

        processor.process(message, session)

        verify(exactly = 1) {
            distributorService.createUserRoomCandidate(message)
            wsSender.saveSessionAndSendNewSessionMessage(USER_ID_1, emptyList(), null, session)
            distributorService.searchCandidatesAndCreateRooms()
        }
        rooms.forEach { room -> verify(exactly = 1) { wsSender.sendRoomCreatedMessage(room) } }
        verifyOrder {
            distributorService.createUserRoomCandidate(message)
            wsSender.saveSessionAndSendNewSessionMessage(USER_ID_1, emptyList(), null, session)
            distributorService.searchCandidatesAndCreateRooms()
        }
        confirmVerified(distributorService, wsSender)
    }

    @Test
    fun `process - does not register session or search rooms when candidate creation fails`() {
        val failure = IllegalStateException("Cannot create candidate")
        every { distributorService.createUserRoomCandidate(any()) } throws failure

        shouldThrow<IllegalStateException> { processor.process(requestForRoomMessage(), session) } shouldBe failure

        verify(exactly = 0) { distributorService.searchCandidatesAndCreateRooms() }
        verify { wsSender wasNot Called }
    }
}
