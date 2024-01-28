package ru.chess.chessapi.controllers

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.chess.chessapi.dto.AuthenticationTokenResponseDto
import ru.chess.chessapi.dto.RegistrationRequestDto
import ru.chess.chessapi.service.RegistrationService
import ru.chess.chessapi.utils.Constants.REGISTRATION_API
import ru.chess.chessapi.utils.Constants.REGISTRATION_CONFIRMATION_PART_API

@RestController
@RequestMapping(REGISTRATION_API)
class RegistrationController(
    private val service: RegistrationService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun registration(@RequestBody registrationRequestDto: RegistrationRequestDto) {
        service.register(registrationRequestDto)
    }

    @GetMapping(REGISTRATION_CONFIRMATION_PART_API)
    fun confirmation(@RequestParam token: String): AuthenticationTokenResponseDto {
        return service.confirm(token)
    }

}