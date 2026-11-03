package ru.chess.chessapi.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import java.util.HexFormat

class CrazyGamesIdentityVerifierTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val keys = FakeKeys(first.public as RSAPublicKey)

    @Test
    fun `valid RSA token yields verified game scoped identity`() {
        val identity = verifier().verify(token())

        assertEquals(VerifiedIdentity("crazygames", "20267", "player-one", "Player.One", "https://images.crazygames.com/avatar.png"), identity)
        assertEquals(1, keys.reads)
        assertEquals(0, keys.refreshes)
    }

    @Test
    fun `wrong signature is rejected after one key refresh`() {
        assertInvalid { verifier().verify(token(key = second)) }
        assertEquals(1, keys.refreshes)
    }

    @Test
    fun `rotated RSA key is retried once and accepted`() {
        keys.next = second.public as RSAPublicKey
        assertEquals("player-one", verifier().verify(token(key = second)).subject)
        assertEquals(1, keys.refreshes)
    }

    @Test
    fun `expired token is rejected without fetching a key`() {
        assertInvalid { verifier().verify(token(claims().expirationTime(Date.from(now)).build())) }
        assertEquals(0, keys.reads)
    }

    @Test
    fun `wrong game is rejected without fetching a key`() {
        assertInvalid { verifier().verify(token(claims().claim("gameId", "other-game").build())) }
        assertEquals(0, keys.reads)
    }

    @Test
    fun `issue and expiration timestamps are required and ordered`() {
        val invalidClaims = listOf(
            claims().issueTime(null).build(),
            claims().expirationTime(null).build(),
            claims().issueTime(Date.from(now.plusSeconds(60))).expirationTime(Date.from(now.plusSeconds(60))).build(),
            claims().issueTime(Date.from(now.plusSeconds(61))).build()
        )
        invalidClaims.forEach { assertInvalid { verifier().verify(token(it)) } }
        assertEquals(0, keys.reads)
    }

    @Test
    fun `small issuer clock skew is allowed`() {
        assertEquals("player-one", verifier().verify(token(claims().issueTime(Date.from(now.plusSeconds(60))).build())).subject)
    }

    @Test
    fun `future not before timestamp is rejected`() {
        assertInvalid { verifier().verify(token(claims().notBeforeTime(Date.from(now.plusSeconds(61))).build())) }
        assertEquals(0, keys.reads)
    }

    @Test
    fun `missing empty and non-string user ids are rejected`() {
        listOf(null, "", " ", 123).forEach { id ->
            assertInvalid { verifier().verify(token(claims().claim("userId", id).build())) }
        }
        assertEquals(0, keys.reads)
    }

    @Test
    fun `wrong algorithm and malformed proof are rejected without fetching a key`() {
        assertInvalid { verifier().verify(token(algorithm = JWSAlgorithm.RS512)) }
        listOf("", "not-a-jwt", "eyJhbGciOiJub25lIn0.e30.", "x".repeat(16_385)).forEach { proof ->
            assertInvalid { verifier().verify(proof) }
        }
        assertEquals(0, keys.reads)
    }

    @Test
    fun `disabled or unconfigured provider is unavailable`() {
        listOf(verifier(enabled = false), verifier(gameId = "")).forEach { verifier ->
            assertUnavailable { verifier.verify(token()) }
        }
        assertEquals(0, keys.reads)
    }

    @Test
    fun `key network failure remains service unavailable`() {
        val unavailableKeys = object : CrazyGamesPublicKeyProvider {
            override fun currentKey(): RSAPublicKey = throw AuthException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE", "Key unavailable")
            override fun refreshKey(rejectedKey: RSAPublicKey): RSAPublicKey = error("Must not refresh after a network failure")
        }
        assertUnavailable { verifier(unavailableKeys).verify(token()) }
    }

    @Test
    fun `HTTP provider accepts SPKI keys and caches and refreshes them on rotation`() {
        val rest = RestTemplate()
        val server = MockRestServiceServer.bindTo(rest).build()
        server.expect(requestTo(HttpCrazyGamesPublicKeyProvider.PUBLIC_KEY_URL)).andRespond(withSuccess(keyResponse(first), MediaType.APPLICATION_JSON))
        server.expect(requestTo(HttpCrazyGamesPublicKeyProvider.PUBLIC_KEY_URL)).andRespond(withSuccess(keyResponse(second), MediaType.APPLICATION_JSON))
        val provider = HttpCrazyGamesPublicKeyProvider(ObjectMapper(), rest, clock)
        val verifier = verifier(provider)

        assertEquals("player-one", verifier.verify(token()).subject)
        assertEquals("player-one", verifier.verify(token()).subject)
        assertEquals("player-one", verifier.verify(token(key = second)).subject)
        assertEquals("player-one", verifier.verify(token(key = second)).subject)
        server.verify()
    }

    @Test
    fun `HTTP provider accepts independent 2048 and official 4096 bit PKCS1 fixtures`() {
        // SPKI fingerprints were calculated independently with Node crypto (OpenSSL).
        val fixtures = listOf(
            Triple("rsa-2048-pkcs1.pem", 2048, "b36f6b82afdee54488f81c2f7e40bb8153d25f776ce1a8b280f827cc6cbd9fa3"),
            Triple("crazygames-4096-pkcs1.pem", 4096, "35cf9f9fc31afb7b28c162d94155fea2807098e7707390171b7f00365c4e23b2")
        )
        fixtures.forEach { (file, bits, fingerprint) ->
            val pem = requireNotNull(javaClass.getResource("/auth/$file")).readText()
            val rest = RestTemplate()
            val server = MockRestServiceServer.bindTo(rest).build()
            server.expect(requestTo(HttpCrazyGamesPublicKeyProvider.PUBLIC_KEY_URL))
                .andRespond(withSuccess(ObjectMapper().writeValueAsString(mapOf("publicKey" to pem)), MediaType.APPLICATION_JSON))

            val key = HttpCrazyGamesPublicKeyProvider(ObjectMapper(), rest, clock).currentKey()

            assertEquals(bits, key.modulus.bitLength(), file)
            assertEquals(65537, key.publicExponent.toInt(), file)
            assertEquals(fingerprint, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.encoded)), file)
            server.verify()
        }
    }

    @Test
    fun `bad signatures cannot repeatedly download the key`() {
        val rest = RestTemplate()
        val server = MockRestServiceServer.bindTo(rest).build()
        repeat(2) {
            server.expect(requestTo(HttpCrazyGamesPublicKeyProvider.PUBLIC_KEY_URL)).andRespond(withSuccess(keyResponse(first), MediaType.APPLICATION_JSON))
        }
        val verifier = verifier(HttpCrazyGamesPublicKeyProvider(ObjectMapper(), rest, clock))

        repeat(3) { assertInvalid { verifier.verify(token(key = second)) } }
        server.verify()
    }

    @Test
    fun `HTTP key failure is unavailable and retries are bounded`() {
        val rest = RestTemplate()
        val server = MockRestServiceServer.bindTo(rest).build()
        server.expect(requestTo(HttpCrazyGamesPublicKeyProvider.PUBLIC_KEY_URL)).andRespond(withServerError())
        val verifier = verifier(HttpCrazyGamesPublicKeyProvider(ObjectMapper(), rest, clock))

        repeat(2) { assertUnavailable { verifier.verify(token()) } }
        server.verify()
    }

    @Test
    fun `rotation network failure remains unavailable while known cached key still works`() {
        val rest = RestTemplate()
        val server = MockRestServiceServer.bindTo(rest).build()
        server.expect(requestTo(HttpCrazyGamesPublicKeyProvider.PUBLIC_KEY_URL)).andRespond(withSuccess(keyResponse(first), MediaType.APPLICATION_JSON))
        server.expect(requestTo(HttpCrazyGamesPublicKeyProvider.PUBLIC_KEY_URL)).andRespond(withServerError())
        val verifier = verifier(HttpCrazyGamesPublicKeyProvider(ObjectMapper(), rest, clock))

        verifier.verify(token())
        repeat(2) { assertUnavailable { verifier.verify(token(key = second)) } }
        assertEquals("player-one", verifier.verify(token()).subject)
        server.verify()
    }

    @Test
    fun `expired key cache reloads from the fixed endpoint`() {
        val rest = RestTemplate()
        val server = MockRestServiceServer.bindTo(rest).build()
        server.expect(requestTo(HttpCrazyGamesPublicKeyProvider.PUBLIC_KEY_URL)).andRespond(withSuccess(keyResponse(first), MediaType.APPLICATION_JSON))
        server.expect(requestTo(HttpCrazyGamesPublicKeyProvider.PUBLIC_KEY_URL)).andRespond(withSuccess(keyResponse(second), MediaType.APPLICATION_JSON))
        val advancingClock = MutableClock(now)
        val provider = HttpCrazyGamesPublicKeyProvider(ObjectMapper(), rest, advancingClock)

        assertEquals(first.public, provider.currentKey())
        advancingClock.current = now.plusSeconds(3600)
        assertEquals(second.public, provider.currentKey())
        server.verify()
    }

    @Test
    fun `failed rotation can recover after the retry interval`() {
        val rest = RestTemplate()
        val server = MockRestServiceServer.bindTo(rest).build()
        server.expect(requestTo(HttpCrazyGamesPublicKeyProvider.PUBLIC_KEY_URL)).andRespond(withSuccess(keyResponse(first), MediaType.APPLICATION_JSON))
        server.expect(requestTo(HttpCrazyGamesPublicKeyProvider.PUBLIC_KEY_URL)).andRespond(withServerError())
        server.expect(requestTo(HttpCrazyGamesPublicKeyProvider.PUBLIC_KEY_URL)).andRespond(withSuccess(keyResponse(second), MediaType.APPLICATION_JSON))
        val advancingClock = MutableClock(now)
        val provider = HttpCrazyGamesPublicKeyProvider(ObjectMapper(), rest, advancingClock)
        val original = provider.currentKey()

        assertUnavailable { provider.refreshKey(original) }
        advancingClock.current = now.plusSeconds(60)
        assertEquals(second.public, provider.refreshKey(original))
        server.verify()
    }

    @Test
    fun `malformed remote key is unavailable rather than an invalid user proof`() {
        val invalidKeys = listOf(
            "not-a-key",
            "-----BEGIN RSA PUBLIC KEY-----\nAQID\n-----END RSA PUBLIC KEY-----",
            "-----BEGIN RSA PUBLIC KEY-----\n%%%\n-----END RSA PUBLIC KEY-----",
            "-----BEGIN RSA PUBLIC KEY-----\nAQID\n-----END PUBLIC KEY-----",
            "-----BEGIN RSA PUBLIC KEY-----\n${Base64.getEncoder().encodeToString(first.public.encoded)}\n-----END RSA PUBLIC KEY-----"
        )
        invalidKeys.forEach { pem ->
            val rest = RestTemplate()
            val server = MockRestServiceServer.bindTo(rest).build()
            server.expect(requestTo(HttpCrazyGamesPublicKeyProvider.PUBLIC_KEY_URL))
                .andRespond(withSuccess(ObjectMapper().writeValueAsString(mapOf("publicKey" to pem)), MediaType.APPLICATION_JSON))

            assertUnavailable { verifier(HttpCrazyGamesPublicKeyProvider(ObjectMapper(), rest, clock)).verify(token()) }
            server.verify()
        }
    }

    private fun verifier(
        provider: CrazyGamesPublicKeyProvider = keys,
        enabled: Boolean = true,
        gameId: String = "20267"
    ) = CrazyGamesIdentityVerifier(provider, enabled, gameId, clock)

    private fun claims() = JWTClaimsSet.Builder()
        .claim("gameId", "20267")
        .claim("userId", "player-one")
        .claim("username", "Player.One")
        .claim("profilePictureUrl", "https://images.crazygames.com/avatar.png")
        .issueTime(Date.from(now.minusSeconds(30)))
        .expirationTime(Date.from(now.plusSeconds(3570)))

    private fun token(
        claims: JWTClaimsSet = claims().build(),
        key: KeyPair = first,
        algorithm: JWSAlgorithm = JWSAlgorithm.RS256
    ): String = SignedJWT(JWSHeader(algorithm), claims).apply {
        sign(RSASSASigner(key.private as RSAPrivateKey))
    }.serialize()

    private fun keyResponse(key: KeyPair): String {
        val pem = "-----BEGIN PUBLIC KEY-----\n${Base64.getEncoder().encodeToString(key.public.encoded)}\n-----END PUBLIC KEY-----"
        return ObjectMapper().writeValueAsString(mapOf("publicKey" to pem))
    }

    private fun assertInvalid(action: () -> Unit) {
        val error = assertThrows(AuthException::class.java) { action() }
        assertEquals(HttpStatus.UNAUTHORIZED, error.status)
        assertEquals("INVALID_PROOF", error.code)
    }

    private fun assertUnavailable(action: () -> Unit) {
        val error = assertThrows(AuthException::class.java) { action() }
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status)
        assertEquals("PROVIDER_UNAVAILABLE", error.code)
    }

    private class FakeKeys(private val current: RSAPublicKey) : CrazyGamesPublicKeyProvider {
        var next = current
        var reads = 0
        var refreshes = 0
        override fun currentKey(): RSAPublicKey { reads++; return current }
        override fun refreshKey(rejectedKey: RSAPublicKey): RSAPublicKey { refreshes++; return next }
    }

    private class MutableClock(var current: Instant) : Clock() {
        override fun instant(): Instant = current
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
    }

    companion object {
        private val first = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        private val second = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }
}
