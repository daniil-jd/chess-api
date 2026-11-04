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
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.TestData.Sessions.SESSION_ID
import ru.chess.chessapi.TestData.Sessions.SESSION_ID_2

@ExtendWith(MockKExtension::class)
class SessionStateServiceTest {
    @InjectMockKs
    private lateinit var service: SessionStateService
    @MockK(relaxUnitFun = true)
    private lateinit var session: WebSocketSession
    @MockK(relaxUnitFun = true)
    private lateinit var otherSession: WebSocketSession

    @BeforeEach
    fun beforeEach() {
        every { session.id } returns SESSION_ID
        every { otherSession.id } returns SESSION_ID_2
    }

    @Test
    fun `get - empty state returns null`() {
        service.get(SESSION_ID) shouldBe null
    }

    @Test
    fun `add - stores separate sessions by id`() {
        service.add(session)
        service.add(otherSession)

        service.get(SESSION_ID) shouldBeSameInstanceAs session
        service.get(SESSION_ID_2) shouldBeSameInstanceAs otherSession
    }

    @Test
    fun `get - unknown id returns null without affecting stored session`() {
        service.add(session)

        service.get(SESSION_ID_2) shouldBe null
        service.get(SESSION_ID) shouldBeSameInstanceAs session
    }

    @Test
    fun `add - replaces session with same id`() {
        every { otherSession.id } returns SESSION_ID
        service.add(session)

        service.add(otherSession)

        service.get(SESSION_ID) shouldBeSameInstanceAs otherSession
    }

    @Test
    fun `remove - removes only requested session`() {
        service.add(session)
        service.add(otherSession)

        service.remove(SESSION_ID)

        service.get(SESSION_ID) shouldBe null
        service.get(SESSION_ID_2) shouldBeSameInstanceAs otherSession
    }

    @Test
    fun `remove - unknown id does not affect stored session`() {
        service.add(session)

        service.remove(SESSION_ID_2)

        service.get(SESSION_ID) shouldBeSameInstanceAs session
    }

    @Test
    fun `remove - empty state and repeated removal are safe`() {
        service.remove(SESSION_ID)
        service.add(session)
        service.remove(SESSION_ID)
        service.remove(SESSION_ID)

        service.get(SESSION_ID) shouldBe null
    }

    @Test
    fun `add - stores new session after removal`() {
        every { otherSession.id } returns SESSION_ID
        service.add(session)
        service.remove(SESSION_ID)

        service.add(otherSession)

        service.get(SESSION_ID) shouldBeSameInstanceAs otherSession
    }
}
