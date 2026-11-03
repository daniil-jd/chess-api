package ru.chess.chessapi.auth

import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import ru.chess.chessapi.entity.UserEntity
import java.util.UUID

/** Resolves access rules, not platform names, at the boundary of game operations. */
@Component
class PlayerAccessGuard {
    fun current(): AuthPrincipal? = SecurityContextHolder.getContext().authentication?.principal as? AuthPrincipal

    fun requireSession(): AuthPrincipal = current()
        ?: throw AuthException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED", "A backend session is required")

    fun requireUser(user: UserEntity) {
        val principal = current()
        if (principal != null) {
            requireMatchingId(user.id)
        } else if (!user.legacyAccessAllowed || user.isBot) {
            throw AuthException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "This profile requires a backend session")
        }
    }

    fun requireMatchingId(requestedId: UUID?) {
        val principal = current() ?: return
        if (requestedId != null && requestedId != principal.userId) {
            throw AuthException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "The requested profile does not belong to this session")
        }
    }

    fun requireLegacyUser(user: UserEntity) {
        if (!user.legacyAccessAllowed) {
            throw AuthException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Legacy login is not available for this profile")
        }
        requireUser(user)
    }
}
