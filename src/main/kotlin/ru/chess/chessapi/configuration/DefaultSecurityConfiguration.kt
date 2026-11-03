package ru.chess.chessapi.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.config.http.SessionCreationPolicy
import com.fasterxml.jackson.databind.ObjectMapper
import ru.chess.chessapi.auth.AuthHttpFilter
import ru.chess.chessapi.auth.AuthService
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
class DefaultSecurityConfiguration {

    @Bean
    fun filterChain(http: HttpSecurity, auth: AuthService, mapper: ObjectMapper): SecurityFilterChain {
        http
            .cors { c -> c.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(AuthHttpFilter(auth, mapper), UsernamePasswordAuthenticationFilter::class.java)
            // Legacy clients still enter without a token; ownership is checked by PlayerAccessGuard.
            .authorizeHttpRequests { it.anyRequest().permitAll() }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val corsConf = CorsConfiguration()
        corsConf.allowedOrigins = listOf("*")
        corsConf.allowedMethods = listOf("*")
        corsConf.allowedHeaders = listOf("*")
        corsConf.exposedHeaders = listOf("Authorization", "Content-Type")

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", corsConf)
        return source
    }
}
