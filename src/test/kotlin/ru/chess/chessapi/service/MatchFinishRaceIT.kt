package ru.chess.chessapi.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import net.javacrumbs.shedlock.core.LockAssert
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import ru.chess.chessapi.entity.RoomEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.exception.ActiveRoomDoesNotExistByUserIdException
import ru.chess.chessapi.repository.RoomRepository
import ru.chess.chessapi.repository.UserRepository
import ru.chess.chessapi.service.messagetype.MatchFinishProcessor
import ru.chess.chessapi.service.scheduler.RoomDeadlineScheduler
import ru.chess.chessapi.service.sessions.RoomAcceptedMessagesStateService
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.web.websocket.message.MatchFinishedMessageDto
import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.GameType
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.RoomStatus
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import javax.sql.DataSource

// Explicit -Dtest=MatchFinishRaceIT keeps Docker-dependent checks out of image builds.
@SpringJUnitConfig(MatchFinishRaceIT.FinishConfiguration::class)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:tc:postgresql:15.19-bookworm:///match-finish-race",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "spring.datasource.username=test",
    "spring.datasource.password=test",
    "spring.datasource.hikari.maximum-pool-size=4",
    "spring.datasource.hikari.connection-timeout=5000",
    "spring.datasource.hikari.connection-init-sql=SET statement_timeout TO '15s'",
    "spring.jpa.open-in-view=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml",
    "scheduling.deadline.enabled=true",
])
class MatchFinishRaceIT {
    companion object {
        private val deadline = Instant.parse("2026-11-12T12:00:00Z")
        private val now = ThreadLocal.withInitial { deadline.minusSeconds(1) }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(
        DataSourceAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        LiquibaseAutoConfiguration::class,
    )
    @EnableTransactionManagement
    @EntityScan(basePackageClasses = [UserEntity::class])
    @EnableJpaRepositories(basePackageClasses = [UserRepository::class])
    @Import(
        MatchFinishProcessor::class, RoomDeadlineScheduler::class, DistributorService::class,
        UserRoomCandidateService::class, UserService::class, RoomService::class,
        AuthCodeService::class, WsSender::class, UserSessionStateService::class,
        RoomAcceptedMessagesStateService::class,
    )
    class FinishConfiguration {
        @Bean
        fun objectMapper(): ObjectMapper = jacksonObjectMapper()

        @Bean
        fun timeService(): TimeService = mockk {
            every { getNowInstant() } answers { now.get() }
            every { getRoomDeadLineInstant() } answers { now.get().plusSeconds(60) }
        }
    }

    @Autowired private lateinit var processor: MatchFinishProcessor
    @Autowired private lateinit var scheduler: RoomDeadlineScheduler
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var roomRepository: RoomRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var sessions: UserSessionStateService
    @Autowired private lateinit var acceptedMoves: RoomAcceptedMessagesStateService

    private lateinit var roomId: UUID
    private lateinit var whiteId: UUID
    private lateinit var blackId: UUID

    @BeforeEach
    fun prepareRoom() {
        TransactionTemplate(transactionManager).executeWithoutResult {
            val white = userRepository.save(UserEntity(username = "white", signature = null, totalPoints = 0))
            val black = userRepository.save(UserEntity(username = "black", signature = null, totalPoints = 0))
            whiteId = white.id!!
            blackId = black.id!!
            roomId = roomRepository.save(RoomEntity(
                user1 = white, user1Side = SideType.WHITE, user1Name = white.username,
                user2 = black, user2Side = SideType.BLACK, user2Name = black.username,
                gameType = GameType.ONLINE, history = "e4", winnerSide = null, winType = null,
                lastMoveSide = SideType.WHITE, roomStatus = RoomStatus.ACTIVE,
                createdAt = deadline.minusSeconds(60), updatedAt = deadline.minusSeconds(60),
                deadlineAt = deadline,
            )).id!!
        }
    }

    @AfterEach
    fun cleanUpRoom() {
        now.remove()
        if (!this::roomId.isInitialized) return
        sessions.removeByUserId(whiteId)
        sessions.removeByUserId(blackId)
        acceptedMoves.clearByRoomId(roomId)
        TransactionTemplate(transactionManager).executeWithoutResult {
            roomRepository.deleteById(roomId)
            userRepository.deleteAllById(listOf(whiteId, blackId))
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    @Timeout(45)
    fun `draw and timeout serialize on the room and emit only the winning result`(timeoutFirst: Boolean) {
        val jdbc = JdbcTemplate(dataSource)
        val whiteMessages = CopyOnWriteArrayList<JsonNode>()
        val blackMessages = CopyOnWriteArrayList<JsonNode>()
        val whiteSession = session("white-$whiteId", whiteMessages)
        sessions.add(whiteId, whiteSession)
        sessions.add(blackId, session("black-$blackId", blackMessages))
        val request = MatchFinishedMessageDto(MessageType.MATCH_FINISHED, roomId, null, FinishType.DRAW)
        val firstReturned = CompletableFuture<Int>()
        val secondEntered = CompletableFuture<Int>()
        val releaseFirst = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        fun finish(timeout: Boolean) {
            // The requests straddle the deadline without relying on wall-clock sleeps.
            now.set(if (timeout) deadline.plusSeconds(1) else deadline.minusSeconds(1))
            if (timeout) scheduler.start() else processor.process(request, whiteSession)
        }

        mockkStatic(LockAssert::class) {
            every { LockAssert.assertLocked() } returns Unit
            try {
                val first = executor.submit<Unit> {
                    try {
                        TransactionTemplate(transactionManager).executeWithoutResult {
                            val pid = jdbc.queryForObject("select pg_backend_pid()", Int::class.java)!!
                            finish(timeoutFirst)
                            firstReturned.complete(pid)
                            check(releaseFirst.await(20, SECONDS)) { "First transaction was not released" }
                        }
                    } catch (failure: Throwable) {
                        firstReturned.completeExceptionally(failure)
                        throw failure
                    } finally {
                        now.remove()
                    }
                }
                val firstPid = firstReturned.get(10, SECONDS)
                val second = executor.submit<Boolean> {
                    try {
                        TransactionTemplate(transactionManager).executeWithoutResult {
                            secondEntered.complete(jdbc.queryForObject("select pg_backend_pid()", Int::class.java)!!)
                            finish(!timeoutFirst)
                        }
                        false
                    } catch (_: ActiveRoomDoesNotExistByUserIdException) {
                        true
                    } finally {
                        now.remove()
                    }
                }
                val secondPid = secondEntered.get(10, SECONDS)
                await().atMost(5, SECONDS).until {
                    jdbc.queryForObject(
                        """select exists(select 1 from pg_stat_activity
                            where pid = ? and ? = any(pg_blocking_pids(pid))
                            and lower(query) like '%for update%')""",
                        Boolean::class.java, secondPid, firstPid,
                    ) == true
                }
                second.isDone shouldBe false
                releaseFirst.countDown()
                first.get(10, SECONDS)
                second.get(10, SECONDS) shouldBe timeoutFirst

                // A repeated scheduler tick or client request must not send another result.
                finish(true)
                shouldThrow<ActiveRoomDoesNotExistByUserIdException> {
                    TransactionTemplate(transactionManager).executeWithoutResult { finish(false) }
                }
                val room = roomRepository.findById(roomId).orElseThrow()
                room.lastMoveSide shouldBe SideType.WHITE
                room.deadlineAt shouldBe deadline
                room.roomStatus shouldBe if (timeoutFirst) RoomStatus.EXPIRED else RoomStatus.FINISHED
                room.winType shouldBe if (timeoutFirst) FinishType.TIMEOUT else FinishType.DRAW
                room.winnerSide shouldBe if (timeoutFirst) SideType.WHITE else null
                room.history shouldBe if (timeoutFirst) "e4" else "e4 DRAW"
                whiteMessages.size shouldBe if (timeoutFirst) 1 else 0
                blackMessages.size shouldBe 1
                (whiteMessages + blackMessages).forEach { message ->
                    message.path("messageType").asText() shouldBe
                        if (timeoutFirst) "MATCH_FINISHED_WS" else "MATCH_FINISHED"
                    message.path("room").asText() shouldBe roomId.toString()
                    message.path("finishType").asText() shouldBe if (timeoutFirst) "TIMEOUT" else "DRAW"
                    if (timeoutFirst) message.path("winnerSide").asText() shouldBe "WHITE"
                    else message.path("winnerSide").isNull shouldBe true
                }
            } finally {
                releaseFirst.countDown()
                executor.shutdownNow()
                check(executor.awaitTermination(20, SECONDS)) { "Finish workers did not stop" }
            }
        }
    }

    private fun session(id: String, messages: MutableList<JsonNode>): WebSocketSession {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.id } returns id
        every { session.sendMessage(any()) } answers {
            messages.add(mapper.readTree(firstArg<TextMessage>().payload))
            Unit
        }
        return session
    }
}
