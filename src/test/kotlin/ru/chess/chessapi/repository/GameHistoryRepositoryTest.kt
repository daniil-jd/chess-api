package ru.chess.chessapi.repository

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import ru.chess.chessapi.BaseSpringTestFull
import ru.chess.chessapi.TestData.GameHistories.CREATED_AT
import ru.chess.chessapi.TestData.Models.Entity.gameHistory
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Models.Entity.userEntity
import ru.chess.chessapi.TestData.Users.AUTH_CODE_2
import ru.chess.chessapi.TestData.Users.SIGNATURE_2
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.TestData.Users.USERNAME_2
import ru.chess.chessapi.web.websocket.message.enums.GameType
import ru.chess.chessapi.web.websocket.message.enums.RoomStatus
import ru.chess.chessapi.web.websocket.message.enums.UserGameStatus

@Disabled("Не работает в гитхабе")
class GameHistoryRepositoryTest : BaseSpringTestFull() {

    @BeforeEach
    fun beforeEach() {
        userRepository.saveAll(
            listOf(
                userEntity(),
                userEntity(username = USERNAME_2, signature = SIGNATURE_2, authCode = AUTH_CODE_2),
            )
        )
    }

    @Test
    fun `maxMatchNumberForUser - maximum across game types for requested user`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val room1 = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
        val room2 = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED, gameType = GameType.BOT))
        gameHistoryRepository.saveAll(
            listOf(
                gameHistory(user1, room1, matchNumber = 3),
                gameHistory(user1, room2, matchNumber = 7),
                gameHistory(user2, room1, matchNumber = 20),
            )
        )

        gameHistoryRepository.maxMatchNumberForUser(user1.id!!) shouldBe 7
    }

    @Test
    fun `maxMatchNumberForUser - no history for user`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val room = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
        gameHistoryRepository.save(gameHistory(user2, room, matchNumber = 5))

        gameHistoryRepository.maxMatchNumberForUser(user1.id!!) shouldBe 0
    }

    @Test
    fun `findByUserAndRoom - matches both user and room`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val room1 = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
        val room2 = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
        val expected = gameHistoryRepository.save(gameHistory(user1, room1, matchNumber = 5, favourite = true, points = 10))
        gameHistoryRepository.saveAll(
            listOf(gameHistory(user2, room1), gameHistory(user1, room2))
        )

        val actual = gameHistoryRepository.findByUserAndRoom(user1, room1)

        actual?.id shouldBe expected.id
        actual?.matchNumber shouldBe 5
        actual?.gameType shouldBe GameType.ONLINE
        actual?.userGameStatus shouldBe UserGameStatus.WIN
        actual?.favourite shouldBe true
        actual?.points shouldBe 10
        actual?.createdAt shouldBe CREATED_AT
    }

    @Test
    fun `findByUserAndRoom - pair not found`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val room1 = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
        val room2 = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
        gameHistoryRepository.saveAll(
            listOf(gameHistory(user2, room1), gameHistory(user1, room2))
        )

        gameHistoryRepository.findByUserAndRoom(user1, room1) shouldBe null
    }

    @Test
    fun `findLatest30RoomsByUserAndGameType - filters and sorts by match number with limit 30`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val histories = (1..31).map { number ->
            val room = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
            gameHistoryRepository.save(
                gameHistory(user1, room, matchNumber = number, createdAt = CREATED_AT.minusSeconds(number.toLong()))
            )
        }
        val otherTypeRoom = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED, gameType = GameType.BOT))
        val otherUserRoom = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
        gameHistoryRepository.saveAll(
            listOf(
                gameHistory(user1, otherTypeRoom, matchNumber = 100),
                gameHistory(user2, otherUserRoom, matchNumber = 200),
            )
        )

        val actual = gameHistoryRepository.findLatest30RoomsByUserAndGameType(user1.id!!, GameType.ONLINE.name)

        actual.map { it.id } shouldBe histories.reversed().take(30).map { it.id }
    }

    @Test
    fun `findLatest30RoomsByUserAndGameType - fewer than 30 matches`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val room = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED, gameType = GameType.BOT))
        val expected = gameHistoryRepository.save(gameHistory(user1, room))

        val actual = gameHistoryRepository.findLatest30RoomsByUserAndGameType(user1.id!!, GameType.BOT.name)

        actual.map { it.id } shouldBe listOf(expected.id)
    }

    @Test
    fun `findLatest30RoomsByUserAndGameType - no matching history`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val room = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
        gameHistoryRepository.save(gameHistory(user1, room))

        gameHistoryRepository.findLatest30RoomsByUserAndGameType(user1.id!!, GameType.BOT.name) shouldBe emptyList()
        gameHistoryRepository.findLatest30RoomsByUserAndGameType(user2.id!!, GameType.ONLINE.name) shouldBe emptyList()
    }

    @Test
    fun `getGameStatsNative - counts outcomes only for requested user and game type`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val statuses = listOf(
            UserGameStatus.WIN, UserGameStatus.WIN, UserGameStatus.LOSE,
            UserGameStatus.DRAW, UserGameStatus.DRAW, UserGameStatus.DRAW,
        )
        statuses.forEachIndexed { index, status ->
            val room = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
            gameHistoryRepository.save(gameHistory(user1, room, matchNumber = index + 1, userGameStatus = status))
            gameHistoryRepository.save(gameHistory(user2, room, matchNumber = index + 1, userGameStatus = UserGameStatus.WIN))
        }
        val botRoom = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED, gameType = GameType.BOT))
        gameHistoryRepository.save(gameHistory(user1, botRoom, userGameStatus = UserGameStatus.LOSE))

        val actual = gameHistoryRepository.getGameStatsNative(user1.id!!, GameType.ONLINE.name)

        actual?.userId shouldBe user1.id
        actual?.gameType shouldBe GameType.ONLINE.name
        actual?.wins shouldBe 2L
        actual?.losses shouldBe 1L
        actual?.draws shouldBe 3L
    }

    @ParameterizedTest
    @EnumSource(UserGameStatus::class)
    fun `getGameStatsNative - zero counts for missing outcomes`(status: UserGameStatus) {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val room = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
        gameHistoryRepository.save(gameHistory(user1, room, userGameStatus = status))

        val actual = gameHistoryRepository.getGameStatsNative(user1.id!!, GameType.ONLINE.name)

        actual?.wins shouldBe if (status == UserGameStatus.WIN) 1L else 0L
        actual?.losses shouldBe if (status == UserGameStatus.LOSE) 1L else 0L
        actual?.draws shouldBe if (status == UserGameStatus.DRAW) 1L else 0L
    }

    @Test
    fun `getGameStatsNative - no matching history`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val room = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
        gameHistoryRepository.save(gameHistory(user1, room))

        gameHistoryRepository.getGameStatsNative(user1.id!!, GameType.BOT.name) shouldBe null
        gameHistoryRepository.getGameStatsNative(user2.id!!, GameType.ONLINE.name) shouldBe null
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `findAllByUserAndFavourite - filters user and favourite across game types`(favourite: Boolean) {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val room1 = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
        val room2 = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED, gameType = GameType.BOT))
        val room3 = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
        val expected = gameHistoryRepository.saveAll(
            listOf(
                gameHistory(user1, room1, favourite = favourite),
                gameHistory(user1, room2, favourite = favourite),
            )
        )
        gameHistoryRepository.saveAll(
            listOf(
                gameHistory(user1, room3, favourite = !favourite),
                gameHistory(user2, room1, favourite = favourite),
            )
        )

        val actual = gameHistoryRepository.findAllByUserAndFavourite(user1, favourite)

        actual.map { it.id } shouldContainExactlyInAnyOrder expected.map { it.id }
    }

    @Test
    fun `findAllByUserAndFavourite - no matching history`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val room = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
        gameHistoryRepository.save(gameHistory(user1, room, favourite = false))

        gameHistoryRepository.findAllByUserAndFavourite(user1, true) shouldBe emptyList()
        gameHistoryRepository.findAllByUserAndFavourite(user2, false) shouldBe emptyList()
    }
}
