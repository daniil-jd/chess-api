package ru.chess.chessapi.service.security

import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import ru.chess.chessapi.security.DefaultAuthenticationToken
import ru.chess.chessapi.security.TokenAuthenticationEntryPoint
import ru.chess.chessapi.utils.Constants.AUTH_TOKEN_HEADER
import ru.chess.chessapi.utils.Constants.PARAMETER_TOKEN
import ru.chess.chessapi.utils.Constants.WEBSOCKET_API
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
class DefaultTokenAuthenticationFilterService(
    private val tokenService: DefaultTokenService,
    private val entryPoint : TokenAuthenticationEntryPoint
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        var token = request.getHeader(AUTH_TOKEN_HEADER)
        if (token.isNullOrEmpty() && request.servletPath.equals(WEBSOCKET_API)) {
            token = request.getParameter(PARAMETER_TOKEN)
        }

        if (token.isNullOrEmpty()) {
            filterChain.doFilter(request, response)
            return
        }
        try {
            val authRequest = DefaultAuthenticationToken(principal = token)
            val authResult = tokenService.authenticate(authRequest)

            SecurityContextHolder.getContext().authentication = authResult
        } catch (ex: AuthenticationException) {
            SecurityContextHolder.clearContext()
            entryPoint.commence(request, response, ex)
            return
        }
        filterChain.doFilter(request, response)
    }


}