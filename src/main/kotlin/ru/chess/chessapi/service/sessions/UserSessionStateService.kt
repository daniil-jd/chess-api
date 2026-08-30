package ru.chess.chessapi.service.sessions

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service
class UserSessionStateService {
    private var userIdToSession = ConcurrentHashMap<UUID, WebSocketSession>()

    private val logger = KotlinLogging.logger {}

    fun add(userId: UUID,session: WebSocketSession) {
        userIdToSession.putIfAbsent(userId, session).also {
            logger.info { "session ${session.id} saved for user $userId" }
        }
    }

    fun getByUserId(userId: UUID): WebSocketSession? {
        return userIdToSession[userId]
    }

    fun removeByUserId(userId: UUID) {
        userIdToSession.remove(userId)
    }

    fun getUserIdBySession(sessionId: String): UUID? {
        logger.info { "trying to find userId by session: $sessionId" }
        userIdToSession.forEach { (userId, wsSession) ->
            if (sessionId == wsSession.id) {
                logger.info { "by session $sessionId found user $userId" }
                return userId
            }
        }
        logger.warn { "by session $sessionId can't find user" }
        return null
    }

    // проверка, что не шлют сообщения из "второго окна", из "старой сессии", новые сессии создаются при WS_RETRY
    fun isUserSessionActive(session: WebSocketSession, message: String): Boolean {
        return if (getUserIdBySession(session.id) == null) {
            logger.warn {
                "some user use inactive session: ${session.id}, message: $message, stop processing this message"
            }
            false
        } else {
            true
        }
    }

}