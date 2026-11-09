package ru.chess.chessapi.utils

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import ru.chess.chessapi.TestData.Models.Entity.roomEntity
import ru.chess.chessapi.TestData.Users.USER_ID_1
import ru.chess.chessapi.TestData.Users.USER_ID_2
import ru.chess.chessapi.TestData.Users.USER_ID_3
import ru.chess.chessapi.web.websocket.message.enums.SideType

class RoomExtensionTest {
    @ParameterizedTest
    @CsvSource("WHITE, BLACK", "BLACK, WHITE")
    fun `findSide - returns assigned side for each player`(first: SideType, second: SideType) {
        val room = roomEntity(user1Side = first, user2Side = second)

        room.findSide(USER_ID_1) shouldBe first
        room.findSide(USER_ID_2) shouldBe second
    }

    @Test
    fun `findSide - returns null for a user outside the room`() {
        roomEntity().findSide(USER_ID_3) shouldBe null
    }

    @ParameterizedTest
    @EnumSource(SideType::class, names = ["WHITE", "BLACK"])
    fun `winnerSideForExpiredRoom - last player to move wins`(lastMove: SideType) {
        roomEntity(lastMoveSide = lastMove).winnerSideForExpiredRoom() shouldBe lastMove
    }

    @Test
    fun `winnerSideForExpiredRoom - black wins without a recorded move`() {
        roomEntity().winnerSideForExpiredRoom() shouldBe SideType.BLACK
    }
}
