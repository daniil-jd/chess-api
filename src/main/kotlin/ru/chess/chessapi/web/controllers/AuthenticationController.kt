package ru.chess.chessapi.web.controllers

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.utils.Constants.AUTHENTICATION_API
import ru.chess.chessapi.web.dto.request.AuthBySignatureRequest
import ru.chess.chessapi.web.dto.response.AuthResponse
import ru.chess.chessapi.web.dto.response.AuthCodeResponse
import java.util.*

@RestController
@RequestMapping(AUTHENTICATION_API)
class AuthenticationController(
    private val distributorService: DistributorService
) {

    @PostMapping("/signature")
    fun bySignature(@RequestBody request: AuthBySignatureRequest): AuthResponse {
        return distributorService.authBySignature(request)
    }

    @PostMapping("/code/generate")
    fun generate(@RequestParam backendUserId: UUID): AuthCodeResponse {
        return distributorService.generateAuthCode(backendUserId)
    }

    @PostMapping("/code/auth")
    fun authByCode(@RequestParam authCode: String): AuthResponse {
        return distributorService.validateAuthCode(authCode)
    }
}