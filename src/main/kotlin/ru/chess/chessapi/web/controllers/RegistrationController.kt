package ru.chess.chessapi.web.controllers

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.chess.chessapi.web.dto.security.AuthenticationTokenResponseDto
import ru.chess.chessapi.web.dto.security.RegistrationRequestDto
import ru.chess.chessapi.service.RegistrationService
import ru.chess.chessapi.utils.Constants.REGISTRATION_API
import ru.chess.chessapi.utils.Constants.REGISTRATION_CONFIRMATION_PART_API
import ru.chess.chessapi.web.dto.request.RegistrationRequest
import ru.chess.chessapi.web.dto.response.RegistrationResponse

@RestController
@RequestMapping(REGISTRATION_API)
class RegistrationController(
    private val service: RegistrationService
) {

    @PostMapping
    fun simpleRegistration(@RequestBody request: RegistrationRequest): RegistrationResponse {
        return service.simpleRegister(request)
    }

}