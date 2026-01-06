package ru.chess.chessapi.web.controllers

import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import ru.chess.chessapi.service.GameHistoryService
import ru.chess.chessapi.web.dto.request.FavouriteRequest
import ru.chess.chessapi.web.dto.response.FavouriteRoomResponse

@RestController
class FavouriteRoomController(
    private val gameHistoryService: GameHistoryService
) {
    @PutMapping("/favourite")
    fun addOrDeleteFavourite(@RequestBody request: FavouriteRequest): FavouriteRoomResponse {
        return gameHistoryService.markOrUnmarkFavourite(request)
    }
}