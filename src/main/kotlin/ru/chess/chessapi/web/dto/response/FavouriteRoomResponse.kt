package ru.chess.chessapi.web.dto.response

data class FavouriteRoomResponse(
    val status: Status
) {
    enum class Status {
        CREATED,
        DELETED
    }
}
