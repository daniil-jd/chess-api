package ru.chess.chessapi.service

import org.springframework.stereotype.Service
import org.sqids.Sqids
import java.util.UUID
import kotlin.math.absoluteValue

@Service
class AuthCodeService {

    private companion object {
        const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXY"
        const val SUBSTRING_START_POS = 0
        const val SUBSTRING_END_POS = 7
        const val DASH = "-"
        const val MAX_DASH_INDEX_VALUE = 5
        const val MIN_DASH_INDEX_VALUE = 1
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
        val preIndex = (MAX_DASH_INDEX_VALUE - squid.hashCode().absoluteValue.toString()[0].toString().toInt()).absoluteValue
        val index = if (preIndex < MIN_DASH_INDEX_VALUE) (preIndex + 1) else preIndex
        return sb
            .insert(index, DASH)
            .substring(SUBSTRING_START_POS, SUBSTRING_END_POS)
    }

}