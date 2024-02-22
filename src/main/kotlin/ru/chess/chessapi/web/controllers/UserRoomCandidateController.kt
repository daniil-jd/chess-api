package ru.chess.chessapi.web.controllers

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.chess.chessapi.web.dto.request.UserRoomCandidateResponseDto
import ru.chess.chessapi.web.dto.request.toDto
import ru.chess.chessapi.entity.UserEntity
import ru.chess.chessapi.service.UserRoomCandidateService

@RestController
@RequestMapping("/api/user-room-candidate")
class UserRoomCandidateController(
    private val userRomCandidateService: UserRoomCandidateService
) {

    // todo fix it one day or delete
//    @PostMapping
//    fun create(@AuthenticationPrincipal user: UserEntity): UserRoomCandidateResponseDto {
//        val userRoomCandidate = userRomCandidateService.createUserRoomCandidate(user)
//        return userRoomCandidate.toDto()
//    }
}
