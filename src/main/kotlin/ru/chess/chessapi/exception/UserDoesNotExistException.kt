package ru.chess.chessapi.exception

class UserDoesNotExistException(val username: String) : RuntimeException(
    "User with username = $username doesn't exist"
)