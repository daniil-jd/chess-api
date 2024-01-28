package ru.chess.chessapi.controllers

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.chess.chessapi.dto.UserRoomCandidateResponseDto
import ru.chess.chessapi.dto.toDto
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.service.UserRoomCandidateService

@RestController
@RequestMapping("/api/user-room-candidate")
class UserRoomCandidateController(
    private val userRomCandidateService: UserRoomCandidateService
) {

    @PostMapping
    fun create(@AuthenticationPrincipal user: UserEntity): UserRoomCandidateResponseDto {
        val userRoomCandidate = userRomCandidateService.createUserRoomCandidate(user)
        return userRoomCandidate.toDto()
    }
}
