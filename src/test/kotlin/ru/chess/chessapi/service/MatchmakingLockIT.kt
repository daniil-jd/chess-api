package ru.chess.chessapi.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.aop.support.AopUtils
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
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.repository.RoomRepository
import ru.chess.chessapi.repository.UserRepository
import ru.chess.chessapi.repository.UserRoomCandidateRepository
import ru.chess.chessapi.service.messagetype.RequestForRoomProcessor
import ru.chess.chessapi.service.sessions.UserSessionStateService
import ru.chess.chessapi.web.websocket.message.RequestForRoomMessageDto
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import javax.sql.DataSource

// Explicit -Dtest=MatchmakingLockIT keeps Docker-dependent checks out of image builds.
@SpringJUnitConfig(MatchmakingLockIT.MatchmakingConfiguration::class)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:tc:postgresql:15.19-bookworm:///matchmaking-lock",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "spring.datasource.username=test",
    "spring.datasource.password=test",
    "spring.datasource.hikari.maximum-pool-size=4",
    "spring.datasource.hikari.connection-timeout=5000",
    "spring.datasource.hikari.connection-init-sql=SET statement_timeout TO '15s'",
    "spring.jpa.open-in-view=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml",
])
class MatchmakingLockIT {
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
        RequestForRoomProcessor::class, DistributorService::class,
        UserRoomCandidateService::class, UserService::class, RoomService::class,
        AuthCodeService::class, TimeService::class, WsSender::class,
        UserSessionStateService::class,
    )
    class MatchmakingConfiguration {
        @Bean
        fun objectMapper(): ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    @Autowired private lateinit var processor: RequestForRoomProcessor
    @Autowired private lateinit var distributor: DistributorService
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var roomRepository: RoomRepository
    @Autowired private lateinit var candidateRepository: UserRoomCandidateRepository

    private lateinit var whiteId: UUID
    private lateinit var blackId: UUID

    @BeforeEach
    fun preparePlayers() {
        TransactionTemplate(transactionManager).executeWithoutResult {
            candidateRepository.deleteAllInBatch()
            roomRepository.deleteAllInBatch()
            userRepository.deleteAllInBatch()
            whiteId = userRepository.save(UserEntity(username = "white", signature = null, totalPoints = 0)).id!!
            blackId = userRepository.save(UserEntity(username = "black", signature = null, totalPoints = 0)).id!!
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    @Timeout(45)
    fun `competing matchmaking waits for outer commit or rollback`(rollbackFirst: Boolean) {
        AopUtils.isAopProxy(distributor) shouldBe true
        val jdbc = JdbcTemplate(dataSource)
        jdbc.queryForObject("select count(*) from matchmaking_lock where id = 1", Long::class.java) shouldBe 1L
        val whiteMessages = CopyOnWriteArrayList<JsonNode>()
        val blackMessages = CopyOnWriteArrayList<JsonNode>()
        val whiteSession = session("white-$whiteId", whiteMessages)
        val blackSession = session("black-$blackId", blackMessages)
        val firstReturned = CompletableFuture<Int>()
        val secondEntered = CompletableFuture<Int>()
        val releaseFirst = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = executor.submit {
                try {
                    // Preserve WSHandler's outer transaction, including work after service methods return.
                    TransactionTemplate(transactionManager).executeWithoutResult { status ->
                        val pid = jdbc.queryForObject("select pg_backend_pid()", Int::class.java)!!
                        processor.process(request(whiteId, SideType.WHITE), whiteSession)
                        firstReturned.complete(pid)
                        check(releaseFirst.await(20, SECONDS)) { "First transaction was not released" }
                        if (rollbackFirst) status.setRollbackOnly()
                    }
                } catch (failure: Throwable) {
                    firstReturned.completeExceptionally(failure)
                    throw failure
                }
            }
            val firstPid = firstReturned.get(10, SECONDS)
            val second = executor.submit {
                TransactionTemplate(transactionManager).executeWithoutResult {
                    secondEntered.complete(jdbc.queryForObject("select pg_backend_pid()", Int::class.java)!!)
                    processor.process(request(blackId, SideType.BLACK), blackSession)
                }
            }
            val secondPid = secondEntered.get(10, SECONDS)
            await().atMost(5, SECONDS).until {
                jdbc.queryForObject(
                    """select exists(select 1 from pg_stat_activity
                        where pid = ? and ? = any(pg_blocking_pids(pid))
                        and query like '%matchmaking_lock%')""",
                    Boolean::class.java, secondPid, firstPid,
                ) == true
            }
            second.isDone shouldBe false
            blackMessages.size shouldBe 0
            roomRepository.count() shouldBe 0L
            candidateRepository.count() shouldBe 0L

            releaseFirst.countDown()
            first.get(10, SECONDS)
            second.get(10, SECONDS)

            if (rollbackFirst) {
                roomRepository.count() shouldBe 0L
                candidateRepository.findAll().map { it.user.id } shouldBe listOf(blackId)
                whiteMessages.count { it.path("messageType").asText() == "ROOM_FOUND" } shouldBe 0
                blackMessages.count { it.path("messageType").asText() == "ROOM_FOUND" } shouldBe 0
                TransactionTemplate(transactionManager).executeWithoutResult {
                    processor.process(request(whiteId, SideType.WHITE), whiteSession)
                }
            }

            val room = roomRepository.findAll().single()
            setOf(room.user1.id, room.user2.id) shouldBe setOf(whiteId, blackId)
            candidateRepository.count() shouldBe 0L
            val foundMessages = listOf(whiteMessages, blackMessages).map { messages ->
                messages.single { it.path("messageType").asText() == "ROOM_FOUND" }
            }
            foundMessages.forEach { found ->
                found.path("room").asText() shouldBe room.id.toString()
                found.path("matchServerTimeMs").isIntegralNumber shouldBe true
                (found.path("matchServerTimeMs").longValue() > 0) shouldBe true
            }
            foundMessages.map { it.path("matchServerTimeMs").longValue() }.distinct().size shouldBe 1
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(20, SECONDS)) { "Matchmaking workers did not stop" }
        }
    }

    private fun request(userId: UUID, side: SideType) = RequestForRoomMessageDto(
        MessageType.REQUEST_FOR_ROOM, null, userId, side.name, side,
    )

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
