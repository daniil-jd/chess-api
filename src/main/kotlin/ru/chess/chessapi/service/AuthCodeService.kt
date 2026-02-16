package ru.chess.chessapi.service

import org.springframework.stereotype.Service
import org.sqids.Sqids
import java.util.*
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
        return bigLong.toString().map { char ->
            char.code.toLong()
        }
    }

    fun substringSquidAndAddDash(squidResult: String): String {
        val squidChunks = squidResult.chunked(size = 20)
        var charSumStringBuilder = StringBuilder(squidChunks[0])
        for (i in 1..<squidChunks.size) {
            charSumStringBuilder = StringBuilder(sumChars(charSumStringBuilder.toString(), squidChunks[i]))
        }
        val preIndex = (MAX_DASH_INDEX_VALUE - charSumStringBuilder.toString().hashCode().absoluteValue.toString()[0].toString().toInt()).absoluteValue
        val index = if (preIndex < MIN_DASH_INDEX_VALUE) (preIndex + 1) else preIndex
        return charSumStringBuilder
            .insert(index, DASH)
            .substring(SUBSTRING_START_POS, SUBSTRING_END_POS)
    }

    fun sumChars(s1: String, s2: String): String {
        val minLength = minOf(s1.length, s2.length)

        return buildString {
            for (i in 0 until minLength) {
                val c1 = s1[i]
                val c2 = s2[i]

                when {
                    c1.isLetter() && c2.isLetter() -> {
                        // буква + буква → буква
                        val base = if (c1.isUpperCase()) 'A' else 'a'
                        val n1 = c1.lowercaseChar() - 'a'
                        val n2 = c2.lowercaseChar() - 'a'
                        val sum = (n1 + n2) % 26
                        append((base + sum).toChar())
                    }
                    c1.isDigit() && c2.isDigit() -> {
                        // цифра + цифра → цифра
                        val n1 = c1.digitToInt()
                        val n2 = c2.digitToInt()
                        val sum = (n1 + n2) % 10
                        append(sum.digitToChar())
                    }
                    c1.isLetter() && c2.isDigit() -> {
                        // буква + цифра → буква
                        val base = if (c1.isUpperCase()) 'A' else 'a'
                        val n1 = c1.lowercaseChar() - 'a'
                        val n2 = c2.digitToInt()
                        val sum = (n1 + n2) % 26
                        append((base + sum).toChar())
                    }
                    c1.isDigit() && c2.isLetter() -> {
                        // цифра + буква → цифра
                        val n1 = c1.digitToInt()
                        val n2 = c2.lowercaseChar() - 'a'  // a=0, b=1, ...
                        val sum = (n1 + n2) % 10
                        append(sum.digitToChar())
                    }
                    else -> {
                        // один или оба символа не буква/цифра — оставляем как есть
                        append(c1)
                    }
                }
            }
            // добавляем "хвост" более длинной строки
            if (s1.length > minLength) append(s1.substring(minLength))
            else if (s2.length > minLength) append(s2.substring(minLength))
        }
    }

}