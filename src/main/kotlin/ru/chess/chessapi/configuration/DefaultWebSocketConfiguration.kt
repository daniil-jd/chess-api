package ru.chess.chessapi.configuration

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import ru.chess.chessapi.utils.Constants.WEBSOCKET_API
import ru.chess.chessapi.web.websocket.WSHandler

@Configuration
@EnableWebSocket
class DefaultWebSocketConfiguration(
    private val handler : WSHandler
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, WEBSOCKET_API).setAllowedOrigins("*")
    }
}
