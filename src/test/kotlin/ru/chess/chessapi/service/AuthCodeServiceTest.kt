package ru.chess.chessapi.service

import io.kotest.matchers.shouldBe
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import ru.chess.chessapi.TestData.AuthCodes
import java.util.UUID

@ExtendWith(MockKExtension::class)
class AuthCodeServiceTest {
    @InjectMockKs
    private lateinit var service: AuthCodeService

    @ParameterizedTest
    @MethodSource("uuids")
    fun `generateShortAuthCode - produces repeatable seven character code with one internal dash`(uuid: UUID) {
        val actual = service.generateShortAuthCode(uuid)

        actual.length shouldBe 7
        actual.count { it == '-' } shouldBe 1
        (actual.indexOf('-') in 1..5) shouldBe true
        actual.filter { it != '-' }.all { it in 'A'..'Z' || it in '0'..'9' } shouldBe true
        service.generateShortAuthCode(uuid) shouldBe actual
    }

    @ParameterizedTest
    @MethodSource("squidCases")
    fun `substringSquidAndAddDash - folds chunks and inserts dash`(input: String, expected: String) {
        service.substringSquidAndAddDash(input) shouldBe expected
    }

    @ParameterizedTest
    @MethodSource("sumCharCases")
    fun `sumChars - combines characters and preserves longer tail`(first: String, second: String, expected: String) {
        service.sumChars(first, second) shouldBe expected
    }

    companion object {
        @JvmStatic
        fun uuids() = AuthCodes.UUIDS.stream()

        @JvmStatic
        fun squidCases() = AuthCodes.SQUID_CASES.map { (input, expected) -> Arguments.of(input, expected) }.stream()

        @JvmStatic
        fun sumCharCases() = AuthCodes.SUM_CHAR_CASES.map { (first, second, expected) ->
            Arguments.of(first, second, expected)
        }.stream()
    }
}
