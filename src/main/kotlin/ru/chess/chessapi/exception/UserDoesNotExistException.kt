package ru.chess.chessapi.exception

class UserDoesNotExistException(val user: String) : RuntimeException(
    "User with username/userId/signature/authCode = $user doesn't exist"
)