package ru.chess.chessapi.service.security

import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.dto.AuthenticationTokenRequestDto
import ru.chess.chessapi.dto.AuthenticationTokenResponseDto
import ru.chess.chessapi.entity.AuthenticationTokenEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.repository.AuthenticationTokenRepository
import ru.chess.chessapi.repository.UserRepository
import java.util.*

@Service
@Transactional
class AuthenticationService(
    private val userRepository: UserRepository,
    private val authenticationTokenRepository: AuthenticationTokenRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun save(user: UserEntity): AuthenticationTokenEntity {
        authenticationTokenRepository.deleteAllByUser(user)
        return authenticationTokenRepository.save(AuthenticationTokenEntity(user = user))
    }

    fun authenticate(authRequest: AuthenticationTokenRequestDto): AuthenticationTokenResponseDto {
        with(authRequest) {
            val user = userRepository.findByUsername(username)
                .orElseThrow { BadCredentialsException("user not found") }
            if (!passwordEncoder.matches(password, user.password)) {
                throw BadCredentialsException("bad credentials")
            }

            val token = UUID.randomUUID()
            val authToken = AuthenticationTokenEntity(user = user)
            authenticationTokenRepository.deleteAllByUser(user)
            authenticationTokenRepository.save(authToken)

            return AuthenticationTokenResponseDto(token.toString())
        }
    }
}
