package ru.chess.chessapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.exception.UserAlreadyExistException
import ru.chess.chessapi.web.dto.request.RegistrationRequest
import ru.chess.chessapi.web.dto.response.RegistrationResponse

@Service
class RegistrationService(
    private val userService: UserService
) {

    @Transactional
    fun simpleRegister(request: RegistrationRequest): RegistrationResponse {
        with(request) {
            val user = userService.findUserByName(name)
            if (user != null) {
                throw UserAlreadyExistException(name)
            }
            val createdUser = userService.save(UserEntity(username = name, signature = signature, rating = 0))
            return RegistrationResponse(
                userId = createdUser.id.toString(),
                number = createdUser.serialNumber!!,
                name = createdUser.username,
                rating = createdUser.rating
            )
        }
    }

}
