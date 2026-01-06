package ru.chess.chessapi.web.controllers

import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.web.dto.request.UserChangeNameRequest

@RestController
class UserController(
    private val distributorService: DistributorService
) {

    @PutMapping("/user/name")
    fun addOrDeleteFavourite(@RequestBody request: UserChangeNameRequest) {
        distributorService.changeUserName(request)
    }
}
