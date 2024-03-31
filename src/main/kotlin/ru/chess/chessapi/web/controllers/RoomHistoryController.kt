package ru.chess.chessapi.web.controllers

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.web.dto.request.RoomHistorySaveRequest

@RestController
class RoomHistoryController(
    private val service: DistributorService
) {

    @PostMapping("/history/save")
    fun saveHistory(@RequestBody request: RoomHistorySaveRequest) {
        service.saveHistory(request)
    }
}
