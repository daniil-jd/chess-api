package ru.chess.chessapi.web.controllers

import com.fasterxml.jackson.databind.JsonMappingException
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

@RestControllerAdvice
class RestControllerErrorHandler {
    private val log = KotlinLogging.logger {}

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
        log.error { e.message }
        val cause: Throwable = e.cause ?: e

        return if (cause is JsonMappingException && cause.path.isNotEmpty()) {
            val fieldName = buildFullFieldNameForJsonMappingExceptionPath(cause.path)

            ErrorResponse(
                "Illegal field value. Field: $fieldName. Reason: ${
                    cause.originalMessage.replace('"', '`')
                }"
            )
        } else {
            ErrorResponse("Json parse error. Reason: ${cause.message?.split("\n")?.firstOrNull() ?: ""}")
        }
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMissingServletRequestPartException(e: MissingServletRequestPartException): ErrorResponse {
        log.error { e.message }
        return ErrorResponse("Bad request: no file found")
    }

    data class ErrorResponse(val errorMessage: String)

    private fun buildFullFieldNameForJsonMappingExceptionPath(path: List<JsonMappingException.Reference>): String {
        val deleteDotAtTheBeginningRegex = """^\.""".toRegex()
        return path.joinToString(separator = "") { reference ->
            reference.fieldName?.let { ".${it}" }
                ?: "[${reference.index}]"
        }.replaceFirst(deleteDotAtTheBeginningRegex, "")
    }

}