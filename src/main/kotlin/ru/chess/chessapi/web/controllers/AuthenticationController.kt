package ru.chess.chessapi.web.controllers

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.chess.chessapi.web.dto.security.AuthenticationTokenRequestDto
import ru.chess.chessapi.web.dto.security.AuthenticationTokenResponseDto
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