package ru.chess.chessapi.service

import org.springframework.stereotype.Service
import org.sqids.Sqids
import java.util.UUID
import kotlin.random.Random

@Service
class AuthCodeService {

    private companion object {
        const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXY"
        const val SUBSTRING_START_POS = 0
        const val SUBSTRING_END_POS = 7
        const val DASH = "-"
    }

    fun generateShortAuthCode(uuidToParse: UUID): String {
        val significantBits = uuidToParse.mostSignificantBits
        val squids = Sqids(alphabet = ALPHABET)
        val squidsResult = squids.encode(splitBigLongToLongList(significantBits))
        return substringSquidAndAddDash(squidsResult)
    }

    private fun splitBigLongToLongList(bigLong: Long): List<Long> {
        return bigLong.toString().substring(SUBSTRING_START_POS, SUBSTRING_END_POS).map { char ->
            char.code.toLong()
        }
    }

    private fun substringSquidAndAddDash(squid: String): String {
        val sb = StringBuilder(squid)
        val randomIndex = Random.nextInt(1, 5)
        return sb
            .insert(randomIndex, DASH)
            .substring(SUBSTRING_START_POS, SUBSTRING_END_POS)
    }

}