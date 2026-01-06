package ru.chess.chessapi.web.controllers

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.chess.chessapi.service.RoomHistorySaveService
import ru.chess.chessapi.service.RoomHistorySearchService
import ru.chess.chessapi.web.dto.request.RoomHistorySaveRequest
import ru.chess.chessapi.web.dto.response.HistoryRoomResponse
import ru.chess.chessapi.web.dto.response.RoomHistorySaveResponse
import ru.chess.chessapi.web.dto.response.RoomHistorySearchResponse
import java.util.*

@RestController
class RoomHistoryController(
    private val roomHistorySaveService: RoomHistorySaveService,
    private val roomHistorySearchService: RoomHistorySearchService
) {

    @PostMapping("/history/save")
    fun saveHistory(@RequestBody request: RoomHistorySaveRequest): RoomHistorySaveResponse {
        return roomHistorySaveService.saveHistory(request)
    }

    @GetMapping("/history")
    fun getLatest20UserHistory(
        @RequestParam backendUserId: String?,
        @RequestParam signature: String?
    ): RoomHistorySearchResponse {
        return roomHistorySearchService.getLatest30GamesEachType(backendUserId, signature)
    }

    @GetMapping("/history/room")
    fun getHistoryRoomById(@RequestParam roomId: UUID): HistoryRoomResponse {
        return roomHistorySearchService.getHistoryRoomById(roomId)
    }
}
