package ru.chess.chessapi.web.controllers

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.chess.chessapi.auth.AuthService
import ru.chess.chessapi.auth.PlayerAccessGuard

@RestController
@RequestMapping("/api/v1/auth")
class UnifiedAuthController(private val auth: AuthService, private val access: PlayerAccessGuard) {
    @PostMapping("/guest/create")
    fun createGuest() = auth.createGuest()

    @PostMapping("/guest/resume")
    fun resumeGuest(@RequestBody request: GuestResumeRequest) = auth.resumeGuest(request.guestKey)

    @PostMapping("/exchange")
    fun exchange(@RequestBody request: ExchangeRequest) =
        auth.exchange(request.provider, request.proof, request.createIfMissing)

    @PostMapping("/identities/link")
    fun link(@RequestBody request: LinkRequest) = auth.link(access.requireSession(), request.provider, request.proof)

    @GetMapping("/me")
    fun me() = auth.me(access.requireSession())

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout() = auth.logout(access.requireSession())

    data class GuestResumeRequest(val guestKey: String)
    data class ExchangeRequest(val provider: String, val proof: String, val createIfMissing: Boolean = false)
    data class LinkRequest(val provider: String, val proof: String)
}
