package ru.chess.chessapi.auth

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class AuthHttpFilter(private val auth: AuthService, private val mapper: ObjectMapper) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val authorization = request.getHeader("Authorization")
        if (authorization != null) {
            try {
                if (!authorization.startsWith("Bearer ", ignoreCase = true)) {
                    throw AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIAL", "Expected a bearer session")
                }
                val principal = auth.authenticate(authorization.substringAfter(' ').trim())
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(principal, null, emptyList())
            } catch (e: AuthException) {
                response.status = e.status.value()
                response.contentType = "application/json"
                mapper.writeValue(response.outputStream, AuthErrorResponse(e.code, e.message ?: "Authentication failed"))
                return
            }
        }
        chain.doFilter(request, response)
    }
}

data class AuthErrorResponse(val code: String, val message: String)
