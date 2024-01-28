package ru.chess.chessapi.utils

object Constants {
    const val WEBSOCKET_API = "/"
    const val AUTHENTICATION_API = "/api/authentication"
    const val REGISTRATION_API = "/api/registration"
    const val REGISTRATION_CONFIRMATION_API = "/api/registration/confirmation"
    const val REGISTRATION_CONFIRMATION_PART_API = "/confirmation"
    const val RECOVERY_API = "/api/recovery"
    const val RECOVERY_CONFIRMATION_API = "/api/recovery/confirmation"
    const val RECOVERY_CONFIRMATION_API_WITH_TOKEN = "/api/recovery/confirmation*"
    const val USERS_API = "/api/users"
    const val ROOMS_API = "/api/rooms"
    const val PROFILE_API = "/api/profile"

    const val AUTH_TOKEN_HEADER = "X-Chess-Token"
    const val PARAMETER_TOKEN = "token"

    const val REGISTRATION_MAX_ATTEMPTS = 3

    const val CANDIDATE_MAX_TIME_TO_LIVE = 90L
}
