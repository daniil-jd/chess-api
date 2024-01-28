package ru.chess.chessapi.service

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.dto.AuthenticationTokenResponseDto
import ru.chess.chessapi.dto.RegistrationRequestDto
import ru.chess.chessapi.entity.RegistrationTokenEntity
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.repository.RegistrationTokenRepository
import ru.chess.chessapi.service.security.AuthenticationService
import ru.chess.chessapi.service.security.DefaultMailService
import ru.chess.chessapi.utils.Constants.REGISTRATION_MAX_ATTEMPTS
import java.lang.RuntimeException
import java.util.*

@Service
class RegistrationService(
    private val registrationTokenRepository: RegistrationTokenRepository,
    private val authenticationService: AuthenticationService,
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder,
    private val defaultMailService: DefaultMailService
) {

    @Transactional
    fun register(registerRequest: RegistrationRequestDto) {
        with(registerRequest) {
            val userOptional = userService.findUserByName(username)

            if (userOptional.isEmpty) {
                val user = UserEntity(
                    username = username,
                    mail = mail,
                    password = passwordEncoder.encode(password),
                    isEnabled = false,
                    userSide = userSide
                )
                val tokenEntity = RegistrationTokenEntity(user = user)
                userService.save(user)
                val tokenValue = registrationTokenRepository.save(tokenEntity).token.toString()
                defaultMailService.sendRegistrationToken(mail = mail, token = tokenValue)
            } else {
                if (userOptional.get().isEnabled) {
                    throw RuntimeException("user already enabled")
                }
                with(userOptional.get()) {
                    if (registrationTokenRepository.findAllByUserId(id!!).size >= REGISTRATION_MAX_ATTEMPTS) {
                        throw RuntimeException("to many registration requests")
                    }
                    val tokenEntity = RegistrationTokenEntity(user = this)
                    val tokenValue = registrationTokenRepository.save(tokenEntity).token.toString()
                    defaultMailService.sendRegistrationToken(mail = mail!!, token = tokenValue)
                }
            }
        }
    }

    @Transactional
    fun confirm(tokenValue: String): AuthenticationTokenResponseDto {
        val tokenEntity = registrationTokenRepository.findById(UUID.fromString(tokenValue))
        if (tokenEntity.isEmpty) {
            throw RuntimeException("registration token not found")
        }
        val userEntity = tokenEntity.get().user
        if (registrationTokenRepository.findAllByUserId(userEntity.id!!).size >= REGISTRATION_MAX_ATTEMPTS) {
            throw RuntimeException("to many registration requests")
        }
        if (userEntity.isEnabled) {
            throw RuntimeException("user already enabled")
        }
        userEntity.isEnabled = true
        val authToken = authenticationService.save(userEntity).token
        return AuthenticationTokenResponseDto(authToken.toString())
    }

}
