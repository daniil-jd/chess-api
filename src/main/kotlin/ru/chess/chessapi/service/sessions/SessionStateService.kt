package ru.chess.chessapi.service.sessions

import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

@Service
class SessionStateService {
    private var sessions = ConcurrentHashMap<String, WebSocketSession>()

    fun add(session: WebSocketSession) {
        sessions[session.id] = session
    }

    fun get(sessionId: String): WebSocketSession? {
        return sessions[sessionId]
    }

    fun remove(sessionId: String) {
        sessions.remove(sessionId)
    }
}