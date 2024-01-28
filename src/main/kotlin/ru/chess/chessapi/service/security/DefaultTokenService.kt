package ru.chess.chessapi.service.security

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import ru.chess.chessapi.repository.AuthenticationTokenRepository
import java.util.*

@Service
class DefaultTokenService(
    private val authenticationTokenRepository: AuthenticationTokenRepository
) : AuthenticationManager {

    override fun authenticate(authentication: Authentication?): Authentication {
        val token = authentication?.principal
        if (token == null) {
            throw AuthenticationCredentialsNotFoundException("token not found")
        }
        val tokenEntity = authenticationTokenRepository
            .findById(UUID.fromString(token.toString()))
            .orElseThrow{ BadCredentialsException("invalid token") }
        val user = tokenEntity.user

        return UsernamePasswordAuthenticationToken(user, user.password)
    }
}