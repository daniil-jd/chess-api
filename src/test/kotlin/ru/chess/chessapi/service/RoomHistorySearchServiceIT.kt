package ru.chess.chessapi.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import ru.chess.chessapi.TestData.Models.Entity.gameHistory
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Models.Entity.userEntity
import ru.chess.chessapi.TestData.Rooms.HISTORY
import ru.chess.chessapi.TestData.Users.AUTH_CODE_2
import ru.chess.chessapi.TestData.Users.SIGNATURE_1
import ru.chess.chessapi.TestData.Users.SIGNATURE_2
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.TestData.Users.USERNAME_2
import ru.chess.chessapi.entity.GameHistory
import ru.chess.chessapi.repository.GameHistoryRepository
import ru.chess.chessapi.repository.RoomRepository
import ru.chess.chessapi.repository.UserRepository
import ru.chess.chessapi.web.dto.response.RoomHistorySearchResponse
import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.GameType
import ru.chess.chessapi.web.websocket.message.enums.RoomStatus
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.util.UUID

// Explicit -Dtest=RoomHistorySearchServiceIT keeps Docker-dependent checks out of image builds.
@SpringJUnitConfig(RoomHistorySearchServiceIT.HistoryConfiguration::class)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:tc:postgresql:15.19-bookworm:///history-transaction",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "spring.datasource.username=test",
    "spring.datasource.password=test",
    "spring.jpa.open-in-view=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml",
])
class RoomHistorySearchServiceIT {
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(
        DataSourceAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        LiquibaseAutoConfiguration::class,
    )
    @EnableTransactionManagement
    @EntityScan(basePackageClasses = [GameHistory::class])
    @EnableJpaRepositories(basePackageClasses = [GameHistoryRepository::class])
    @Import(
        RoomHistorySearchService::class, RoomService::class, GameHistoryService::class,
        UserService::class, TimeService::class,
    )
    class HistoryConfiguration

    @Autowired private lateinit var service: RoomHistorySearchService
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var roomRepository: RoomRepository
    @Autowired private lateinit var historyRepository: GameHistoryRepository

    private lateinit var userId: UUID
    private lateinit var favouriteRoomId: UUID
    private lateinit var otherRoomId: UUID

    @BeforeEach
    fun prepareCommittedHistory() {
        TransactionTemplate(transactionManager).executeWithoutResult {
            historyRepository.deleteAllInBatch()
            roomRepository.deleteAllInBatch()
            userRepository.deleteAllInBatch()

            val user = userRepository.save(userEntity(points = 150))
            val opponent = userRepository.save(userEntity(
                username = USERNAME_2, signature = SIGNATURE_2, authCode = AUTH_CODE_2,
            ))
            val favouriteRoom = roomRepository.save(roomEntity(
                user, opponent, roomStatus = RoomStatus.FINISHED, history = HISTORY,
                winType = FinishType.CHECKMATE, winnerSide = SideType.WHITE,
            ))
            val otherRoom = roomRepository.save(roomEntity(
                user, opponent, roomStatus = RoomStatus.FINISHED, history = HISTORY,
                winType = FinishType.CHECKMATE, winnerSide = SideType.WHITE,
            ))
            historyRepository.saveAllAndFlush(listOf(
                gameHistory(user, favouriteRoom, matchNumber = 1, favourite = true),
                gameHistory(user, otherRoom, matchNumber = 2),
            ))
            userId = user.id!!
            favouriteRoomId = favouriteRoom.id!!
            otherRoomId = otherRoom.id!!
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `history and favourites are mapped through the service transaction`(byId: Boolean) {
        AopUtils.isAopProxy(service) shouldBe true
        // No surrounding test transaction may keep detached relations usable accidentally.
        TransactionSynchronizationManager.isActualTransactionActive() shouldBe false

        val response = service.getLatest30GamesEachType(
            if (byId) userId.toString() else null,
            if (byId) null else SIGNATURE_1,
        )

        TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
        response.backendUserId shouldBe userId
        response.signature shouldBe SIGNATURE_1
        response.points shouldBe 150L
        response.matchesHistory.map { it.roomId } shouldBe listOf(otherRoomId, favouriteRoomId)
        response.matchStatistics shouldBe listOf(RoomHistorySearchResponse.MatchStatistic(GameType.ONLINE, 2, 0, 0))
        response.favouritesHistory shouldBe listOf(response.matchesHistory.last())
        response.favouritesHistory.single().favourite shouldBe true
        response.matchesHistory.forEach { match ->
            match.history shouldBe HISTORY
            match.userName shouldBe USERNAME_1
            match.opponentName shouldBe USERNAME_2
            match.userSide shouldBe SideType.WHITE
            match.finishType shouldBe FinishType.CHECKMATE
            match.winnerSide shouldBe SideType.WHITE
        }
    }
}
