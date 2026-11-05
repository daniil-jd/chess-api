package ru.chess.chessapi

import io.mockk.clearAllMocks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import ru.chess.chessapi.repository.GameHistoryRepository
import ru.chess.chessapi.repository.RoomRepository
import ru.chess.chessapi.repository.UserRepository
import ru.chess.chessapi.repository.UserRoomCandidateRepository

/**
 * Use this class as base for all tests which require full application context.
 * You can add @Autowired fields to implementations, but not @MockkBean
 */
@SpringBootTest
@ActiveProfiles("it")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaseSpringTestFull {

    @Autowired
    protected lateinit var gameHistoryRepository: GameHistoryRepository
    @Autowired
    protected lateinit var roomRepository: RoomRepository
    @Autowired
    protected lateinit var userRepository: UserRepository
    @Autowired
    protected lateinit var userRoomCandidateRepository: UserRoomCandidateRepository

    @BeforeEach
    fun beforeEachBase() {
        gameHistoryRepository.deleteAll()
        roomRepository.deleteAll()
        userRoomCandidateRepository.deleteAll()
        userRepository.deleteAll()
    }

    @AfterEach
    fun afterEach() {
        clearAllMocks() // https://github.com/Ninja-Squad/springmockk/issues/85
    }
}
