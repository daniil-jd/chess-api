package ru.chess.chessapi.service

class BookService {

    fun tt() {
        val books = listOf<String>()
        val map = mutableMapOf<String, Int>()
        books.forEach {
            if (map[it] == null) {
                map.putIfAbsent(it, 1)
            } else {
                map[it] = map[it]!! + 1
            }
        }

    }

}