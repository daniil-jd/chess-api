package ru.chess.chessapi.web.controllers

import org.springframework.web.bind.annotation.*
import ru.chess.chessapi.service.DistributorService
import ru.chess.chessapi.web.dto.request.RoomHistorySaveRequest
import ru.chess.chessapi.web.dto.response.HistoryRoomResponse
import ru.chess.chessapi.web.dto.response.RoomHistorySaveResponse
import ru.chess.chessapi.web.dto.response.RoomHistorySearchResponse
import java.util.UUID

@RestController
class RoomHistoryController(
    private val service: DistributorService
) {

    @PostMapping("/history/save")
    fun saveHistory(@RequestBody request: RoomHistorySaveRequest): RoomHistorySaveResponse {
        return service.saveHistory(request)
    }

    @GetMapping("/history")
    fun getLatest20UserHistory(
        @RequestParam backendUserId: String?,
        @RequestParam signature: String?
    ): RoomHistorySearchResponse {
        return service.getLatest30GamesEachType(backendUserId, signature)
    }

    @GetMapping("/history/room")
    fun getHistoryRoomById(@RequestParam roomId: UUID): HistoryRoomResponse {
        return service.getHistoryRoomById(roomId)
    }
}
