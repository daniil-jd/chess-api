package ru.chess.chessapi.auth

import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

internal data class StoredIdentity(val id: UUID, val userId: UUID, val identity: VerifiedIdentity)
internal data class StoredCredential(val id: UUID, val userId: UUID, val revokedAt: Instant?)

@Repository
class AuthStore(private val jdbc: JdbcTemplate) {
    // Serialize absent-identity creation too; a row lock cannot protect a row that does not exist yet.
    internal fun lockIdentity(identity: VerifiedIdentity) {
        val name = listOf(identity.provider, identity.namespace, identity.subject)
            .joinToString("") { "${it.length}:$it" }
        val key = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))).long
        jdbc.execute(ConnectionCallback { connection ->
            connection.prepareStatement("select pg_advisory_xact_lock(?)").use {
                it.setLong(1, key)
                it.execute()
            }
            Unit
        })
    }

    internal fun lockUser(userId: UUID): Boolean = jdbc.query(
        "select id from chess_users_2 where id = ? for update",
        { rs, _ -> rs.getObject("id", UUID::class.java) }, userId
    ).isNotEmpty()

    internal fun findIdentity(identity: VerifiedIdentity): StoredIdentity? = jdbc.query(
        "select * from auth_identities where provider = ? and namespace = ? and subject = ?",
        { rs, _ -> readIdentity(rs) }, identity.provider, identity.namespace, identity.subject
    ).singleOrNull()

    internal fun findIdentity(id: UUID): StoredIdentity? = jdbc.query(
        "select * from auth_identities where id = ?", { rs, _ -> readIdentity(rs) }, id
    ).singleOrNull()

    internal fun createIdentity(userId: UUID, identity: VerifiedIdentity, now: Instant): StoredIdentity {
        val id = UUID.randomUUID()
        jdbc.update(
            """insert into auth_identities
                (id, user_id, provider, namespace, subject, display_name, avatar_url, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)""",
            id, userId, identity.provider, identity.namespace, identity.subject,
            identity.displayName, identity.avatarUrl, Timestamp.from(now)
        )
        return StoredIdentity(id, userId, identity)
    }

    internal fun updateIdentityProfile(id: UUID, identity: VerifiedIdentity) {
        jdbc.update("update auth_identities set display_name = ?, avatar_url = ? where id = ?",
            identity.displayName, identity.avatarUrl, id)
    }

    internal fun createCredential(userId: UUID, hash: String, now: Instant): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """insert into auth_credentials (id, user_id, type, secret_hash, created_at)
                values (?, ?, 'GUEST_RECOVERY', ?, ?)""", id, userId, hash, Timestamp.from(now)
        )
        return id
    }

    internal fun findCredential(hash: String): StoredCredential? = jdbc.query(
        "select id, user_id, revoked_at from auth_credentials where secret_hash = ? and type = 'GUEST_RECOVERY'",
        { rs, _ -> StoredCredential(rs.getObject("id", UUID::class.java),
            rs.getObject("user_id", UUID::class.java), rs.getTimestamp("revoked_at")?.toInstant()) }, hash
    ).singleOrNull()

    internal fun createSession(principal: AuthPrincipal, hash: String, now: Instant) {
        jdbc.update(
            """insert into auth_sessions
                (id, user_id, identity_id, credential_id, token_hash, created_at, expires_at)
                values (?, ?, ?, ?, ?, ?, ?)""",
            principal.authSessionId, principal.userId, principal.identityId, principal.credentialId,
            hash, Timestamp.from(now), Timestamp.from(principal.expiresAt)
        )
    }

    internal fun findActiveSessionByToken(hash: String, now: Instant): AuthPrincipal? = activeSessions(
        "s.token_hash = ?", hash, now
    )

    internal fun findActiveSessionById(id: UUID, now: Instant): AuthPrincipal? = activeSessions(
        "s.id = ?", id, now
    )

    private fun activeSessions(predicate: String, value: Any, now: Instant): AuthPrincipal? = jdbc.query(
        """select s.* from auth_sessions s
            join chess_users_2 u on u.id = s.user_id
            left join auth_credentials c on c.id = s.credential_id
            where $predicate and s.revoked_at is null and s.expires_at > ?
              and u.legacy_access_allowed = false
              and (s.credential_id is null or c.revoked_at is null)""",
        { rs, _ -> AuthPrincipal(rs.getObject("user_id", UUID::class.java),
            rs.getObject("id", UUID::class.java), rs.getTimestamp("expires_at").toInstant(),
            rs.getObject("identity_id", UUID::class.java), rs.getObject("credential_id", UUID::class.java)) },
        value, Timestamp.from(now)
    ).singleOrNull()

    internal fun revokeSession(id: UUID, userId: UUID, now: Instant): List<UUID> = jdbc.query(
        """update auth_sessions set revoked_at = ? where id = ? and user_id = ? and revoked_at is null
            returning id""",
        { rs, _ -> rs.getObject("id", UUID::class.java) }, Timestamp.from(now), id, userId
    )

    internal fun revokeGuestAccess(userId: UUID, now: Instant): List<UUID> {
        jdbc.update("""update auth_credentials set revoked_at = ?
            where user_id = ? and type = 'GUEST_RECOVERY' and revoked_at is null""",
            Timestamp.from(now), userId)
        return jdbc.query(
            """update auth_sessions set revoked_at = ?
                where user_id = ? and revoked_at is null
                  and credential_id in (select id from auth_credentials where type = 'GUEST_RECOVERY') returning id""",
            { rs, _ -> rs.getObject("id", UUID::class.java) }, Timestamp.from(now), userId
        )
    }

    internal fun hasActiveGameOrSearch(userId: UUID): Boolean = jdbc.queryForObject(
        """select exists(select 1 from chess_rooms_2
                where (user_1 = ? or user_2 = ?) and game_type = 'ONLINE' and win_type is null)
            or exists(select 1 from user_room_candidates_2 where user_id = ? and active_until >= ?)""",
        Boolean::class.java, userId, userId, userId, Timestamp.valueOf(LocalDateTime.now())
    ) == true

    private fun readIdentity(rs: ResultSet) = StoredIdentity(
        rs.getObject("id", UUID::class.java), rs.getObject("user_id", UUID::class.java),
        VerifiedIdentity(rs.getString("provider"), rs.getString("namespace"), rs.getString("subject"),
            rs.getString("display_name"), rs.getString("avatar_url"))
    )
}
