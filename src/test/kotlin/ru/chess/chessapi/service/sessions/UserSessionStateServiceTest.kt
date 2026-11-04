package ru.chess.chessapi.service.sessions

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ru.chess.chessapi.TestData.Sessions.OLD_SESSION_ID
import ru.chess.chessapi.TestData.Sessions.SESSION_ID
import ru.chess.chessapi.TestData.Models.Message.MOVE
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2
import org.springframework.web.socket.WebSocketSession

@ExtendWith(MockKExtension::class)
class UserSessionStateServiceTest {
    @InjectMockKs
    private lateinit var service: UserSessionStateService
    @MockK(relaxUnitFun = true)
    private lateinit var session: WebSocketSession
    @MockK(relaxUnitFun = true)
    private lateinit var otherSession: WebSocketSession

    @BeforeEach
    fun beforeEach() {
        every { session.id } returns SESSION_ID
        every { otherSession.id } returns OLD_SESSION_ID
    }

    @Test
    fun `add - stores separate sessions for users`() {
        service.add(USER_ID_1, session)
        service.add(USER_ID_2, otherSession)

        service.getByUserId(USER_ID_1) shouldBeSameInstanceAs session
        service.getByUserId(USER_ID_2) shouldBeSameInstanceAs otherSession
    }

    @Test
    fun `add - preserves existing session for same user`() {
        service.add(USER_ID_1, session)

        service.add(USER_ID_1, otherSession)

        service.getByUserId(USER_ID_1) shouldBeSameInstanceAs session
        service.getUserIdBySession(OLD_SESSION_ID) shouldBe null
    }

    @Test
    fun `getByUserId - unknown user`() {
        service.add(USER_ID_1, session)

        service.getByUserId(USER_ID_2) shouldBe null
    }

    @Test
    fun `getUserIdBySession - finds correct user among multiple sessions`() {
        service.add(USER_ID_1, session)
        service.add(USER_ID_2, otherSession)

        service.getUserIdBySession(SESSION_ID) shouldBe USER_ID_1
        service.getUserIdBySession(OLD_SESSION_ID) shouldBe USER_ID_2
    }

    @Test
    fun `getUserIdBySession - unknown session`() {
        service.add(USER_ID_1, session)

        service.getUserIdBySession(OLD_SESSION_ID) shouldBe null
    }

    @Test
    fun `removeByUserId - removes mapping in both lookup directions`() {
        service.add(USER_ID_1, session)
        service.add(USER_ID_2, otherSession)

        service.removeByUserId(USER_ID_1)

        service.getByUserId(USER_ID_1) shouldBe null
        service.getUserIdBySession(SESSION_ID) shouldBe null
        service.getByUserId(USER_ID_2) shouldBeSameInstanceAs otherSession
    }

    @Test
    fun `removeByUserId - unknown user does not affect other users`() {
        service.add(USER_ID_1, session)

        service.removeByUserId(USER_ID_2)

        service.getByUserId(USER_ID_1) shouldBeSameInstanceAs session
    }

    @Test
    fun `add - accepts replacement after old session removed`() {
        service.add(USER_ID_1, session)
        service.removeByUserId(USER_ID_1)

        service.add(USER_ID_1, otherSession)

        service.getByUserId(USER_ID_1) shouldBeSameInstanceAs otherSession
        service.getUserIdBySession(SESSION_ID) shouldBe null
        service.getUserIdBySession(OLD_SESSION_ID) shouldBe USER_ID_1
    }

    @Test
    fun `isUserSessionActive - registered session`() {
        service.add(USER_ID_1, session)

        service.isUserSessionActive(session, MOVE) shouldBe true
    }

    @Test
    fun `isUserSessionActive - unregistered session`() {
        service.add(USER_ID_1, session)

        service.isUserSessionActive(otherSession, MOVE) shouldBe false
    }

    @Test
    fun `isUserSessionActive - removed session`() {
        service.add(USER_ID_1, session)
        service.removeByUserId(USER_ID_1)

        service.isUserSessionActive(session, MOVE) shouldBe false
    }
}
