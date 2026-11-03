package ru.chess.chessapi.auth

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

data class AuthPrincipal(
    val userId: UUID,
    val authSessionId: UUID,
    val expiresAt: Instant,
    val identityId: UUID? = null,
    val credentialId: UUID? = null
)

data class VerifiedIdentity(
    val provider: String,
    val namespace: String,
    val subject: String,
    val displayName: String? = null,
    val avatarUrl: String? = null
)

interface IdentityVerifier {
    val provider: String
    fun verify(proof: String): VerifiedIdentity
}

data class AuthProfile(val playerName: String, val totalPoints: Long)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuthIdentityProfile(
    val provider: String,
    val displayName: String? = null,
    val avatarUrl: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuthResponse(
    val backendUserId: UUID,
    val authSessionId: UUID,
    val sessionToken: String,
    val sessionExpiresAt: Instant,
    val profile: AuthProfile,
    val identityProfile: AuthIdentityProfile? = null,
    val guestKey: String? = null
) {
    override fun toString() = "AuthResponse(backendUserId=$backendUserId, authSessionId=$authSessionId)"
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuthMeResponse(
    val backendUserId: UUID,
    val authSessionId: UUID,
    val sessionExpiresAt: Instant,
    val profile: AuthProfile,
    val identityProfile: AuthIdentityProfile? = null
)

class AuthException(val status: HttpStatus, val code: String, message: String) : RuntimeException(message)

/** Emitted after commit, so an interrupted transaction never closes a working socket. */
data class AuthSessionsRevoked(val userId: UUID, val sessionIds: List<UUID>)
