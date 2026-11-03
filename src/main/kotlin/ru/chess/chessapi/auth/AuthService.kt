package ru.chess.chessapi.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.repository.UserRepository
import ru.chess.chessapi.service.MatchmakingLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

@Service
class AuthService(
    private val store: AuthStore,
    private val users: UserRepository,
    verifiers: List<IdentityVerifier>,
    private val clock: Clock,
    private val events: ApplicationEventPublisher,
    private val matchmakingLock: MatchmakingLock,
    @Value("\${auth.new-accounts-enabled:false}") private val newAccountsEnabled: Boolean,
    @Value("\${auth.session-ttl-seconds:3600}") private val sessionTtlSeconds: Long
) {
    private val verifiersByProvider = verifiers.associateBy { it.provider }
    private val random = SecureRandom()

    init {
        require(sessionTtlSeconds > 0) { "Auth session TTL must be positive" }
        require(verifiersByProvider.size == verifiers.size) { "Identity verifier providers must be unique" }
    }

    @Transactional
    fun createGuest(): AuthResponse {
        requireNewAccountsEnabled()
        val user = createUser()
        val key = randomSecret()
        val credentialId = store.createCredential(user.id!!, hash(key), now())
        return issueSession(user, credentialId = credentialId, guestKey = key)
    }

    @Transactional
    fun resumeGuest(key: String): AuthResponse {
        if (!SECRET_PATTERN.matches(key)) throw invalidGuestKey()
        val hashed = hash(key)
        val credential = store.findCredential(hashed) ?: throw invalidGuestKey()
        store.lockUser(credential.userId)
        // Linking and resuming use the same user lock; never issue a session after guest access was revoked.
        val current = store.findCredential(hashed) ?: throw invalidGuestKey()
        if (current.revokedAt != null) throw invalidGuestKey()
        val user = protectedUser(current.userId)
        return issueSession(user, credentialId = current.id)
    }

    @Transactional
    fun exchange(provider: String, proof: String, createIfMissing: Boolean = false): AuthResponse {
        val verified = verify(provider, proof)
        store.lockIdentity(verified)
        val existing = store.findIdentity(verified)
        if (existing != null) {
            store.lockUser(existing.userId)
            val user = protectedUser(existing.userId)
            store.updateIdentityProfile(existing.id, verified)
            return issueSession(user, identity = existing.copy(identity = verified))
        }
        if (!createIfMissing) throw AuthException(HttpStatus.NOT_FOUND, "IDENTITY_NOT_FOUND", "Identity is not linked")
        requireNewAccountsEnabled()
        val user = createUser()
        val identity = store.createIdentity(user.id!!, verified, now())
        return issueSession(user, identity = identity)
    }

    @Transactional
    fun link(principal: AuthPrincipal, provider: String, proof: String): AuthResponse {
        val verified = verify(provider, proof)
        store.lockIdentity(verified)
        matchmakingLock.acquire()
        lockAndValidate(principal)
        val user = protectedUser(principal.userId)
        val existing = store.findIdentity(verified)
        if (existing != null && existing.userId != user.id) {
            throw AuthException(HttpStatus.CONFLICT, "IDENTITY_ALREADY_LINKED", "Identity belongs to another profile")
        }
        if (store.hasActiveGameOrSearch(user.id!!)) {
            throw AuthException(HttpStatus.CONFLICT, "ACTIVE_MATCH", "Finish the match or cancel matchmaking before linking")
        }
        val identity = existing?.also { store.updateIdentityProfile(it.id, verified) }
            ?.copy(identity = verified) ?: store.createIdentity(user.id!!, verified, now())
        val revoked = store.revokeGuestAccess(user.id!!, now())
        publishRevoked(user.id!!, revoked)
        return issueSession(user, identity = identity)
    }

    @Transactional(readOnly = true)
    fun authenticate(token: String): AuthPrincipal {
        if (!SECRET_PATTERN.matches(token)) throw invalidSession()
        return store.findActiveSessionByToken(hash(token), now()) ?: throw invalidSession()
    }

    /** Keep authorization and its dependent mutation ordered with logout and identity linking. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun lockAndValidate(principal: AuthPrincipal): AuthPrincipal {
        if (!store.lockUser(principal.userId)) throw invalidSession()
        val current = requireCurrentSession(principal)
        protectedUser(current.userId)
        return current
    }

    @Transactional
    fun logout(principal: AuthPrincipal) {
        if (!store.lockUser(principal.userId)) throw invalidSession()
        publishRevoked(principal.userId, store.revokeSession(principal.authSessionId, principal.userId, now()))
    }

    @Transactional(readOnly = true)
    fun me(principal: AuthPrincipal): AuthMeResponse {
        val current = requireCurrentSession(principal)
        val user = protectedUser(current.userId)
        val identity = current.identityId?.let { store.findIdentity(it) }
        return AuthMeResponse(user.id!!, current.authSessionId, current.expiresAt, profile(user),
            identity?.identity?.let { identityProfile(it) })
    }

    private fun verify(provider: String, proof: String): VerifiedIdentity {
        val verifier = verifiersByProvider[provider]
            ?: throw AuthException(HttpStatus.BAD_REQUEST, "PROVIDER_UNSUPPORTED", "Unsupported identity provider")
        if (proof.isBlank() || proof.length > 65_536) {
            throw AuthException(HttpStatus.BAD_REQUEST, "INVALID_PROOF", "Invalid identity proof")
        }
        val result = verifier.verify(proof)
        if (result.provider != provider || result.namespace.isBlank() || result.subject.isBlank() ||
            result.provider.length > 64 || result.namespace.length > 255 || result.subject.length > 512 ||
            listOf(result.provider, result.namespace, result.subject).any { '\u0000' in it }) {
            throw AuthException(HttpStatus.UNAUTHORIZED, "INVALID_PROOF", "Invalid verified identity")
        }
        return result
    }

    private fun createUser(): UserEntity = users.saveAndFlush(UserEntity(
        username = "Player-${UUID.randomUUID().toString().take(8)}", signature = null,
        totalPoints = 0, legacyAccessAllowed = false
    ))

    private fun protectedUser(id: UUID): UserEntity {
        val user = users.findById(id).orElseThrow { invalidSession() }
        if (user.legacyAccessAllowed || user.isBot) throw invalidSession()
        return user
    }

    private fun requireCurrentSession(principal: AuthPrincipal): AuthPrincipal {
        val current = store.findActiveSessionById(principal.authSessionId, now()) ?: throw invalidSession()
        if (current.userId != principal.userId || current.identityId != principal.identityId ||
            current.credentialId != principal.credentialId) throw invalidSession()
        return current
    }

    private fun issueSession(user: UserEntity, identity: StoredIdentity? = null, credentialId: UUID? = null,
                             guestKey: String? = null): AuthResponse {
        check((identity == null) != (credentialId == null))
        val timestamp = now()
        val token = randomSecret()
        val principal = AuthPrincipal(user.id!!, UUID.randomUUID(), timestamp.plusSeconds(sessionTtlSeconds),
            identity?.id, credentialId)
        store.createSession(principal, hash(token), timestamp)
        return AuthResponse(user.id!!, principal.authSessionId, token, principal.expiresAt, profile(user),
            identity?.identity?.let { identityProfile(it) }, guestKey)
    }

    private fun publishRevoked(userId: UUID, sessionIds: List<UUID>) {
        if (sessionIds.isEmpty()) return
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() { events.publishEvent(AuthSessionsRevoked(userId, sessionIds)) }
        })
    }

    private fun requireNewAccountsEnabled() {
        if (!newAccountsEnabled) throw AuthException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_CREATION_DISABLED",
            "New account creation is temporarily disabled")
    }

    private fun profile(user: UserEntity) = AuthProfile(user.username, user.totalPoints)
    private fun identityProfile(identity: VerifiedIdentity) =
        AuthIdentityProfile(identity.provider, identity.displayName, identity.avatarUrl)
    private fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.MICROS)
    private fun randomSecret(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun invalidSession() = AuthException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "Session is invalid or expired")
    private fun invalidGuestKey() = AuthException(HttpStatus.UNAUTHORIZED, "GUEST_KEY_INVALID", "Guest key is invalid or revoked")

    private companion object {
        val SECRET_PATTERN = Regex("[A-Za-z0-9_-]{43}")
    }
}
