package ru.chess.chessapi.controllers

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.chess.chessapi.dto.AuthenticationTokenRequestDto
import ru.chess.chessapi.dto.AuthenticationTokenResponseDto
import ru.chess.chessapi.service.security.AuthenticationService
import ru.chess.chessapi.utils.Constants.AUTHENTICATION_API

@RestController
@RequestMapping(AUTHENTICATION_API)
class AuthenticationController(
    private val service: AuthenticationService
) {
    @PostMapping
    fun authenticate(@RequestBody authRequest: AuthenticationTokenRequestDto): AuthenticationTokenResponseDto {
        return service.authenticate(authRequest)
    }
}