package ru.chess.chessapi.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import ru.chess.chessapi.service.security.DefaultTokenAuthenticationFilterService
import ru.chess.chessapi.utils.Constants.AUTHENTICATION_API
import ru.chess.chessapi.utils.Constants.RECOVERY_API
import ru.chess.chessapi.utils.Constants.RECOVERY_CONFIRMATION_API_WITH_TOKEN
import ru.chess.chessapi.utils.Constants.REGISTRATION_API
import ru.chess.chessapi.utils.Constants.REGISTRATION_CONFIRMATION_API
import ru.chess.chessapi.utils.Constants.WEBSOCKET_API

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
class DefaultSecurityConfiguration(
    private val tokenFilter : DefaultTokenAuthenticationFilterService
) {

    /**
     * Add security filter to chain of filters and secure main endpoints.
     * Enable it then it needs.
     */
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf().disable()
            .cors()
//            .and()
//            .addFilterAfter(tokenFilter, BasicAuthenticationFilter::class.java)
//            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)

            .and()
            .authorizeRequests()
            .antMatchers(WEBSOCKET_API).anonymous()

            .antMatchers(HttpMethod.POST, AUTHENTICATION_API).anonymous()
            .antMatchers(HttpMethod.POST, REGISTRATION_API).anonymous()
            .antMatchers(HttpMethod.GET, REGISTRATION_CONFIRMATION_API).anonymous()
            .antMatchers(HttpMethod.POST, RECOVERY_API).anonymous()
            .antMatchers(HttpMethod.POST, RECOVERY_CONFIRMATION_API_WITH_TOKEN).anonymous()
            .antMatchers(HttpMethod.GET, "/*").anonymous()
//            .antMatchers(USERS_API).authenticated()
//            .antMatchers(ROOMS_API).authenticated()
//            .antMatchers(PROFILE_API).authenticated()
//            .anyRequest().authenticated()
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun corsConfigurationSoruce(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", CorsConfiguration().applyPermitDefaultValues())
        return source
    }
}
