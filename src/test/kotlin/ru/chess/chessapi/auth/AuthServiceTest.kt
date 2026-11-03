package ru.chess.chessapi.auth

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.repository.UserRepository
import ru.chess.chessapi.service.MatchmakingLock
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class AuthServiceTest {
    private val timestamp = Instant.parse("2026-10-13T12:00:00Z")
    private val store = Mockito.mock(AuthStore::class.java)
    private val users = Mockito.mock(UserRepository::class.java)
    private val events = Mockito.mock(ApplicationEventPublisher::class.java)
    private val matchmakingLock = Mockito.mock(MatchmakingLock::class.java)
    private val verified = VerifiedIdentity("test", "game", "account", "Platform name", "https://example.test/avatar")
    private val verifier = object : IdentityVerifier {
        override val provider = "test"
        override fun verify(proof: String) = verified
    }
    private val service = service()

    @Test
    fun `disabled creation never writes a user`() {
        val error = assertThrows(AuthException::class.java) { service(false).createGuest() }

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status)
        assertEquals("AUTH_CREATION_DISABLED", error.code)
        Mockito.verifyNoInteractions(users, store)
    }

    @Test
    fun `invalid guest key never falls back to creating a user`() {
        val error = assertThrows(AuthException::class.java) { service.resumeGuest("not-a-secret") }

        assertEquals("GUEST_KEY_INVALID", error.code)
        Mockito.verifyNoInteractions(users, store)
    }

    @Test
    fun `resume rechecks revocation after acquiring the user lock`() {
        val key = "a".repeat(43)
        val credential = StoredCredential(UUID.randomUUID(), UUID.randomUUID(), null)
        Mockito.`when`(store.findCredential(hash(key))).thenReturn(credential, credential.copy(revokedAt = timestamp))

        assertEquals("GUEST_KEY_INVALID", assertThrows(AuthException::class.java) { service.resumeGuest(key) }.code)

        Mockito.verify(store).lockUser(credential.userId)
        Mockito.verifyNoInteractions(users)
    }

    @Test
    fun `existing-only exchange never creates an identity or user`() {
        val error = assertThrows(AuthException::class.java) { service.exchange("test", "proof", false) }

        assertEquals(HttpStatus.NOT_FOUND, error.status)
        assertEquals("IDENTITY_NOT_FOUND", error.code)
        Mockito.verify(store).lockIdentity(verified)
        Mockito.verifyNoInteractions(users)
    }

    @Test
    fun `identity can not silently claim a different user`() {
        val user = UserEntity(id = UUID.randomUUID(), username = "Guest", signature = null,
            totalPoints = 0, legacyAccessAllowed = false)
        val principal = AuthPrincipal(user.id!!, UUID.randomUUID(), timestamp.plusSeconds(60),
            credentialId = UUID.randomUUID())
        Mockito.`when`(store.lockUser(user.id!!)).thenReturn(true)
        Mockito.`when`(store.findActiveSessionById(principal.authSessionId, timestamp)).thenReturn(principal)
        Mockito.`when`(users.findById(user.id!!)).thenReturn(Optional.of(user))
        Mockito.`when`(store.findIdentity(verified)).thenReturn(StoredIdentity(UUID.randomUUID(), UUID.randomUUID(), verified))

        val error = assertThrows(AuthException::class.java) { service.link(principal, "test", "proof") }

        assertEquals(HttpStatus.CONFLICT, error.status)
        assertEquals("IDENTITY_ALREADY_LINKED", error.code)
        Mockito.verify(store, Mockito.never()).revokeGuestAccess(user.id!!, timestamp)
    }

    @Test
    fun `unknown provider is rejected before any storage access`() {
        val error = assertThrows(AuthException::class.java) { service.exchange("yandex", "unverified-signature") }

        assertEquals("PROVIDER_UNSUPPORTED", error.code)
        Mockito.verifyNoInteractions(users, store)
    }

    @Test
    fun `me does not elevate a legacy user even if a session record exists`() {
        val user = UserEntity(id = UUID.randomUUID(), username = "Legacy", signature = "legacy", totalPoints = 0)
        val principal = AuthPrincipal(user.id!!, UUID.randomUUID(), timestamp.plusSeconds(60),
            credentialId = UUID.randomUUID())
        Mockito.`when`(store.findActiveSessionById(principal.authSessionId, timestamp)).thenReturn(principal)
        Mockito.`when`(users.findById(user.id!!)).thenReturn(Optional.of(user))

        assertEquals("SESSION_INVALID", assertThrows(AuthException::class.java) { service.me(principal) }.code)
    }

    @Test
    fun `mutation validation checks session again only after obtaining the user lock`() {
        val principal = AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), timestamp.plusSeconds(60),
            credentialId = UUID.randomUUID())
        Mockito.`when`(store.lockUser(principal.userId)).thenReturn(true)

        assertEquals("SESSION_INVALID", assertThrows(AuthException::class.java) {
            service.lockAndValidate(principal)
        }.code)

        Mockito.inOrder(store).apply {
            verify(store).lockUser(principal.userId)
            verify(store).findActiveSessionById(principal.authSessionId, timestamp)
        }
        Mockito.verifyNoInteractions(users)
    }

    private fun service(enabled: Boolean = true) = AuthService(store, users, listOf(verifier),
        Clock.fixed(timestamp, ZoneOffset.UTC), events, matchmakingLock, enabled, 3600)

    private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
