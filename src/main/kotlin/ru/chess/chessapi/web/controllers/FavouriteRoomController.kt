package ru.chess.chessapi.web.controllers

import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.web.dto.request.FavouriteRequest

@RestController
class FavouriteRoomController(
    private val distributorService: DistributorService
) {
    @PutMapping("/favourite")
    fun addOrDeleteFavourite(@RequestBody request: FavouriteRequest) {
        distributorService.addOrDeleteFavourite(request)
    }
}