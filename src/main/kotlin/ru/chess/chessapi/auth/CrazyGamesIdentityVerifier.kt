package ru.chess.chessapi.auth

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.security.interfaces.RSAPublicKey
import java.text.ParseException
import java.time.Clock

@Component
class CrazyGamesIdentityVerifier internal constructor(
    private val keys: CrazyGamesPublicKeyProvider,
    private val enabled: Boolean,
    private val gameId: String,
    private val clock: Clock
) : IdentityVerifier {
    @Autowired
    constructor(
        keys: CrazyGamesPublicKeyProvider,
        @Value("\${auth.crazygames.enabled:false}") enabled: Boolean,
        @Value("\${auth.crazygames.game-id:}") gameId: String
    ) : this(keys, enabled, gameId, Clock.systemUTC())

    override val provider = "crazygames"

    override fun verify(proof: String): VerifiedIdentity {
        if (!enabled || gameId.isBlank()) {
            throw AuthException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE", "CrazyGames login is unavailable")
        }
        if (proof.isBlank() || proof.length > 16_384) throw invalidProof()
        try {
            val jwt = SignedJWT.parse(proof)
            if (jwt.header.algorithm != JWSAlgorithm.RS256 || !jwt.header.criticalParams.isNullOrEmpty()) {
                throw invalidProof()
            }
            // Reject malformed claims before a network fetch; only a verified token can produce an identity.
            val claims = jwt.jwtClaimsSet
            val issuedAt = claims.issueTime?.toInstant() ?: throw invalidProof()
            val expiresAt = claims.expirationTime?.toInstant() ?: throw invalidProof()
            val now = clock.instant()
            if (!expiresAt.isAfter(now) || !expiresAt.isAfter(issuedAt) || issuedAt.isAfter(now.plusSeconds(60))) {
                throw invalidProof()
            }
            if (claims.notBeforeTime?.toInstant()?.isAfter(now.plusSeconds(60)) == true) throw invalidProof()
            if (claims.getStringClaim("gameId") != gameId) throw invalidProof()
            val subject = claims.getStringClaim("userId")?.takeIf { it.isNotBlank() && it.length <= 255 }
                ?: throw invalidProof()
            val displayName = claims.getStringClaim("username")
            val avatarUrl = claims.getStringClaim("profilePictureUrl")

            val key = keys.currentKey()
            if (!hasValidSignature(jwt, key) && !hasValidSignature(jwt, keys.refreshKey(key))) {
                throw invalidProof()
            }
            return VerifiedIdentity(provider, gameId, subject, displayName, avatarUrl)
        } catch (_: ParseException) {
            throw invalidProof()
        } catch (_: JOSEException) {
            throw invalidProof()
        }
    }

    private fun hasValidSignature(jwt: SignedJWT, key: RSAPublicKey): Boolean = jwt.verify(RSASSAVerifier(key))

    private fun invalidProof() = AuthException(HttpStatus.UNAUTHORIZED, "INVALID_PROOF", "Invalid CrazyGames proof")
}
