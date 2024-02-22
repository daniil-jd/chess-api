package ru.chess.chessapi.exception

class UserAlreadyExistException(val username: String) : RuntimeException(
    "User with username already exists, username: $username"
)