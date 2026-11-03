package ru.chess.chessapi.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.time.Instant
import java.util.Base64

interface CrazyGamesPublicKeyProvider {
    fun currentKey(): RSAPublicKey
    fun refreshKey(rejectedKey: RSAPublicKey): RSAPublicKey
}

@Component
class HttpCrazyGamesPublicKeyProvider internal constructor(
    private val objectMapper: ObjectMapper,
    private val http: RestOperations,
    private val clock: Clock
) : CrazyGamesPublicKeyProvider {
    @Autowired
    constructor(objectMapper: ObjectMapper) : this(
        objectMapper,
        RestTemplate(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(2_000)
            setReadTimeout(4_000)
        }),
        Clock.systemUTC()
    )

    private var cachedKey: RSAPublicKey? = null
    private var expiresAt = Instant.MIN
    private var nextFetchAt = Instant.MIN
    private var lastFetchFailed = false

    @Synchronized
    override fun currentKey(): RSAPublicKey {
        val now = clock.instant()
        cachedKey?.takeIf { now.isBefore(expiresAt) }?.let { return it }
        if (now.isBefore(nextFetchAt)) throw unavailable()
        nextFetchAt = now.plusSeconds(10)
        return fetchKey(now).also { nextFetchAt = Instant.MIN }
    }

    @Synchronized
    override fun refreshKey(rejectedKey: RSAPublicKey): RSAPublicKey {
        cachedKey?.takeIf { it != rejectedKey }?.let { return it }
        val now = clock.instant()
        if (now.isBefore(nextFetchAt)) {
            if (lastFetchFailed) throw unavailable()
            return cachedKey ?: throw unavailable()
        }
        // Invalid signatures must not turn every login request into an outbound key request.
        nextFetchAt = now.plusSeconds(60)
        return fetchKey(now)
    }

    private fun fetchKey(now: Instant): RSAPublicKey {
        try {
            val body = http.getForObject(PUBLIC_KEY_URL, String::class.java) ?: throw IllegalArgumentException()
            val value = objectMapper.readTree(body)?.get("publicKey")
            require(value?.isTextual == true)
            val pem = value!!.textValue().trim()
            val type = if (pem.startsWith("-----BEGIN RSA PUBLIC KEY-----")) "RSA PUBLIC KEY" else "PUBLIC KEY"
            require(pem.startsWith("-----BEGIN $type-----") && pem.endsWith("-----END $type-----"))
            val encoded = pem.removePrefix("-----BEGIN $type-----")
                .removeSuffix("-----END $type-----").replace(Regex("\\s"), "")
            val decoded = Base64.getDecoder().decode(encoded)
            val spki = if (type == "RSA PUBLIC KEY") {
                // Wrap PKCS#1 in SubjectPublicKeyInfo; JDK still validates the RSA key itself.
                val rsaAlgorithm = byteArrayOf(0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00)
                der(0x30, rsaAlgorithm + der(0x03, byteArrayOf(0) + decoded))
            } else decoded
            val key = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(spki)) as RSAPublicKey
            require(key.modulus.bitLength() >= 2048)
            cachedKey = key
            expiresAt = now.plusSeconds(3600)
            lastFetchFailed = false
            return key
        } catch (_: Exception) {
            lastFetchFailed = true
            throw unavailable()
        }
    }

    private fun der(tag: Int, content: ByteArray): ByteArray {
        val length = if (content.size < 128) byteArrayOf(content.size.toByte()) else {
            val bytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(content.size).array()
                .dropWhile { it == 0.toByte() }.toByteArray()
            byteArrayOf((0x80 or bytes.size).toByte()) + bytes
        }
        return byteArrayOf(tag.toByte()) + length + content
    }

    private fun unavailable() = AuthException(
        HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE", "CrazyGames verification key is unavailable"
    )

    companion object {
        const val PUBLIC_KEY_URL = "https://sdk.crazygames.com/publicKey.json"
    }
}
