package ru.chess.chessapi.web.controllers

import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.support.MissingServletRequestPartException
import ru.chess.chessapi.exception.UserDoesNotExistException
import org.springframework.http.ResponseEntity
import ru.chess.chessapi.auth.AuthException
import ru.chess.chessapi.auth.AuthErrorResponse

@RestControllerAdvice
class RestControllerErrorHandler {
    private val log = KotlinLogging.logger {}

    @ExceptionHandler(AuthException::class)
    fun handleAuthError(e: AuthException): ResponseEntity<AuthErrorResponse> =
        ResponseEntity.status(e.status).body(AuthErrorResponse(e.code, e.message ?: "Authentication failed"))

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleInternalServerError(e: Exception): ErrorResponse {
        log.error { e.message }
        return ErrorResponse("Internal Server Error: Unexpected error occurred")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMethodArgumentNotValidException(e: MethodArgumentNotValidException): ErrorResponse {
        log.error { e.message }
        val reasons = e.bindingResult.fieldErrors.joinToString { it.field + " - " + it.defaultMessage }
        val message = "Validation failed. Reasons (${e.bindingResult.errorCount}): $reasons"
        return ErrorResponse(message)
    }

    @ExceptionHandler(UserDoesNotExistException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleUserDoesNotExistException(e: UserDoesNotExistException): ErrorResponse {
        log.error { e.message }
        return ErrorResponse("Bad request: user not found")
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMissingServletRequestParameterException(e: MissingServletRequestParameterException): ErrorResponse {
        log.error { e.message }
        return ErrorResponse("Bad request: required parameter ${e.parameterName} is missing")
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun jsonParseError(e: HttpMessageNotReadableException): ErrorResponse {
        // Parser diagnostics can contain the submitted proof or recovery key.
        return ErrorResponse("Invalid JSON request")
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMissingServletRequestPartException(e: MissingServletRequestPartException): ErrorResponse {
        log.error { e.message }
        return ErrorResponse("Bad request: no file found")
    }

    data class ErrorResponse(val errorMessage: String)

}
