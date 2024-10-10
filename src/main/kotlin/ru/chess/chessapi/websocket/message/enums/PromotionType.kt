package ru.chess.chessapi.websocket.message.enums

enum class PromotionType {
    QUEEN, ROOK, BISHOP, KNIGHT;

    fun toHistoryPart(): String {
        if (this.name != KNIGHT.name) {
            return this.name.first().toString()
        } else {
            return "N"
        }
    }
}