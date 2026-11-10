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
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Models.Entity.userEntity
import ru.chess.chessapi.TestData.Rooms.CREATED_AT
import ru.chess.chessapi.TestData.Rooms.CURRENT_INSTANT
import ru.chess.chessapi.TestData.Users.AUTH_CODE_2
import ru.chess.chessapi.TestData.Users.SIGNATURE_2
import ru.chess.chessapi.TestData.Users.USERNAME_1
import ru.chess.chessapi.TestData.Users.USERNAME_2
import ru.chess.chessapi.TestData.Users.USERNAME_3
import ru.chess.chessapi.TestData.Users.USERNAME_4
import ru.chess.chessapi.web.websocket.message.enums.GameType
import ru.chess.chessapi.web.websocket.message.enums.RoomStatus
import java.util.UUID

@Disabled("Не работает в гитхабе")
class RoomRepositoryTest : BaseSpringTestFull() {

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
    fun `findByExpiredDeadline - returns only active rooms at or past their deadline`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val room = roomEntity(user1 = user1, user2 = user2)
        val atDeadline = roomRepository.save(room.copy(deadlineAt = CURRENT_INSTANT))
        val overdue = roomRepository.save(room.copy(deadlineAt = CURRENT_INSTANT.minusSeconds(1)))
        roomRepository.saveAll(
            listOf(
                room.copy(deadlineAt = CURRENT_INSTANT.plusSeconds(1)),
                room.copy(deadlineAt = null),
                room.copy(deadlineAt = CURRENT_INSTANT, roomStatus = RoomStatus.FINISHED),
                room.copy(deadlineAt = CURRENT_INSTANT, roomStatus = RoomStatus.EXPIRED)
            )
        )

        val expired = roomRepository.findByExpiredDeadline(CURRENT_INSTANT)

        expired.map { it.id } shouldContainExactlyInAnyOrder listOf(atDeadline.id, overdue.id)
        expired.all { it.roomStatus == RoomStatus.ACTIVE } shouldBe true
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `findAllByUser1AndUser2ForUpdate - both player orders`(reverse: Boolean) {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val user3 = userRepository.save(userEntity(username = USERNAME_3, signature = null, authCode = null))
        val activeRoom = roomRepository.save(roomEntity(user1, user2))
        val reversedActiveRoom = roomRepository.save(roomEntity(user2, user1))
        roomRepository.saveAll(listOf(
            roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED),
            roomEntity(user2, user1, roomStatus = RoomStatus.EXPIRED),
            roomEntity(user1, user3),
        ))

        val first = if (reverse) user2 else user1
        val second = if (reverse) user1 else user2

        val actual = roomRepository.findAllByUser1AndUser2ForUpdate(first.id!!, second.id!!)

        actual.map { it.id } shouldContainExactlyInAnyOrder listOf(activeRoom.id, reversedActiveRoom.id)
    }

    @Test
    fun `findAllByUser1AndUser2ForUpdate - pair not found`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val user3 = userRepository.save(userEntity(username = USERNAME_3, signature = null, authCode = null))
        roomRepository.save(roomEntity(user1, user2))

        val actual = roomRepository.findAllByUser1AndUser2ForUpdate(user1.id!!, user3.id!!)

        actual shouldBe emptyList()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `findByUser - non active room in either player position`(reverse: Boolean) {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val finishedRoom = roomRepository.save(roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED))
        roomRepository.save(roomEntity(user1, user2))
        val user = if (reverse) user2 else user1

        roomRepository.findByUser(user.id!!)?.id shouldBe finishedRoom.id
    }

    @Test
    fun `findByUser - expired room`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val expiredRoom = roomRepository.save(roomEntity(user2, user1, roomStatus = RoomStatus.EXPIRED))
        roomRepository.save(roomEntity(user1, user2))

        roomRepository.findByUser(user1.id!!)?.id shouldBe expiredRoom.id
    }

    @Test
    fun `findByUser - only active rooms`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        roomRepository.save(roomEntity(user1, user2))

        roomRepository.findByUser(user1.id!!) shouldBe null
    }

    @Test
    fun `findByUser - unknown user`() {
        roomRepository.findByUser(UUID.randomUUID()) shouldBe null
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `findActiveByUserForUpdate - either player position`(reverse: Boolean) {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val activeRoom = roomRepository.save(roomEntity(user1, user2))
        roomRepository.saveAll(listOf(
            roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED),
            roomEntity(user2, user1, roomStatus = RoomStatus.EXPIRED),
        ))
        val user = if (reverse) user2 else user1

        val actual = roomRepository.findActiveByUserForUpdate(user.id!!, CURRENT_INSTANT)

        actual?.id shouldBe activeRoom.id
    }

    @ParameterizedTest
    @ValueSource(longs = [-1, 0])
    fun `findActiveByUserForUpdate - deadline reached`(seconds: Long) {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        roomRepository.save(roomEntity(user1, user2, deadlineAt = CURRENT_INSTANT.plusSeconds(seconds)))

        val actual = roomRepository.findActiveByUserForUpdate(user1.id!!, CURRENT_INSTANT)

        actual shouldBe null
    }

    @Test
    fun `findActiveByUserForUpdate - no active room`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        roomRepository.saveAll(listOf(
            roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED),
            roomEntity(user2, user1, roomStatus = RoomStatus.EXPIRED),
        ))

        roomRepository.findActiveByUserForUpdate(user1.id!!, CURRENT_INSTANT) shouldBe null
    }

    @Test
    fun `findActiveByUserForUpdate - unknown user`() {
        roomRepository.findActiveByUserForUpdate(UUID.randomUUID(), CURRENT_INSTANT) shouldBe null
    }

    @Test
    fun `findActiveForUpdate - success`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val activeRoom = roomRepository.save(roomEntity(user1, user2))

        val actual = roomRepository.findActiveForUpdate(activeRoom.id!!, CURRENT_INSTANT)

        actual?.id shouldBe activeRoom.id
    }

    @ParameterizedTest
    @ValueSource(longs = [-1, 0])
    fun `findActiveForUpdate - deadline reached`(seconds: Long) {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val activeRoom = roomRepository.save(roomEntity(user1, user2, deadlineAt = CURRENT_INSTANT.plusSeconds(seconds)))

        roomRepository.findActiveForUpdate(activeRoom.id!!, CURRENT_INSTANT) shouldBe null
    }

    @ParameterizedTest
    @EnumSource(RoomStatus::class, names = ["FINISHED", "EXPIRED"])
    fun `findActiveForUpdate - non active room`(status: RoomStatus) {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val activeRoom = roomRepository.save(roomEntity(user1, user2, roomStatus = status))

        roomRepository.findActiveForUpdate(activeRoom.id!!, CURRENT_INSTANT) shouldBe null
    }

    @Test
    fun `findActiveForUpdate - unknown room`() {
        roomRepository.findActiveForUpdate(UUID.randomUUID(), CURRENT_INSTANT) shouldBe null
    }

    @Test
    fun `active queries - room without deadline`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val activeRoom = roomRepository.save(roomEntity(user1, user2, deadlineAt = null))

        roomRepository.findActiveForUpdate(activeRoom.id!!, CURRENT_INSTANT) shouldBe null
        roomRepository.findActiveByUserForUpdate(user1.id!!, CURRENT_INSTANT) shouldBe null
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `findLatest30RoomsByUser - filters and sorts latest 30`(reverse: Boolean) {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val user3 = userRepository.save(userEntity(username = USERNAME_3, signature = null, authCode = null))
        val user4 = userRepository.save(userEntity(username = USERNAME_4, signature = null, authCode = null))
        roomRepository.saveAll(listOf(
            roomEntity(user1, user2, createdAt = CURRENT_INSTANT),
            roomEntity(user1, user2, gameType = GameType.BOT, roomStatus = RoomStatus.FINISHED, createdAt = CURRENT_INSTANT),
            roomEntity(user3, user4, roomStatus = RoomStatus.FINISHED, createdAt = CURRENT_INSTANT),
        ))
        val rooms = roomRepository.saveAll(
            (1..31).map { index ->
                roomEntity(
                    user1 = if (reverse) user2 else user1,
                    user2 = if (reverse) user1 else user2,
                    roomStatus = if (index % 2 == 0) RoomStatus.FINISHED else RoomStatus.EXPIRED,
                    createdAt = CREATED_AT.plusSeconds(index.toLong())
                )
            }
        )

        val actual = roomRepository.findLatest30RoomsByUser(user1.id!!, GameType.ONLINE.name)

        actual.map { it.id } shouldBe rooms.reversed().take(30).map { it.id }
    }

    @Test
    fun `findLatest30RoomsByUser - filters game type`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val expiredRoom = roomRepository.save(roomEntity(user2, user1, roomStatus = RoomStatus.EXPIRED, gameType = GameType.BOT))
        roomRepository.saveAll(listOf(
            roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED),
            roomEntity(user1, user2, gameType = GameType.BOT),
        ))

        val actual = roomRepository.findLatest30RoomsByUser(user1.id!!, GameType.BOT.name)

        actual.map { it.id } shouldBe listOf(expiredRoom.id)
    }

    @Test
    fun `findLatest30RoomsByUser - unknown user`() {
        roomRepository.findLatest30RoomsByUser(UUID.randomUUID(), GameType.ONLINE.name) shouldBe emptyList()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `countByUser - counts non active rooms in either position`(reverse: Boolean) {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        val user3 = userRepository.save(userEntity(username = USERNAME_3, signature = null, authCode = null))
        val user4 = userRepository.save(userEntity(username = USERNAME_4, signature = null, authCode = null))
        roomRepository.saveAll(listOf(
            roomEntity(user1, user2),
            roomEntity(user1, user2, roomStatus = RoomStatus.FINISHED),
            roomEntity(user2, user1, roomStatus = RoomStatus.EXPIRED),
            roomEntity(user3, user4, roomStatus = RoomStatus.FINISHED),
        ))
        val user = if (reverse) user2 else user1

        roomRepository.countByUser(user.id!!) shouldBe 2L
    }

    @Test
    fun `countByUser - only active rooms`() {
        val user1 = userRepository.findByUsername(USERNAME_1)!!
        val user2 = userRepository.findByUsername(USERNAME_2)!!
        roomRepository.save(roomEntity(user1, user2))

        roomRepository.countByUser(user1.id!!) shouldBe 0L
    }

    @Test
    fun `countByUser - unknown user`() {
        roomRepository.countByUser(UUID.randomUUID()) shouldBe 0L
    }
}
