package ru.chess.chessapi.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Real HTTP, WebSocket and Liquibase against an explicitly supplied disposable PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "auth.new-accounts-enabled=true", "auth.session-ttl-seconds=3600"
])
@Import(UnifiedAuthIntegrationTest.IdentityTestConfiguration::class)
@EnabledIfEnvironmentVariable(named = "AUTH_TEST_DATABASE_URL", matches = ".+")
@Execution(ExecutionMode.SAME_THREAD)
class UnifiedAuthIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val sockets = mutableListOf<SocketClient>()
    private val users = mutableSetOf<UUID>()
    private val rooms = mutableSetOf<UUID>()

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { System.getenv("AUTH_TEST_DATABASE_URL") }
            registry.add("spring.datasource.username") { System.getenv("AUTH_TEST_DATABASE_USERNAME") }
            registry.add("spring.datasource.password") { System.getenv("AUTH_TEST_DATABASE_PASSWORD") }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class IdentityTestConfiguration {
        @Bean
        fun testIdentityVerifier(): IdentityVerifier = object : IdentityVerifier {
            override val provider = "test"
            override fun verify(proof: String): VerifiedIdentity {
                if (proof == "invalid") throw AuthException(HttpStatus.UNAUTHORIZED, "PROOF_INVALID", "Invalid test proof")
                return VerifiedIdentity(provider, "integration", proof, "Verified player")
            }
        }
    }

    @BeforeEach
    fun seedBotsUsedByExistingHistoryFlow() {
        listOf("white_bot", "black_bot").forEach { name ->
            jdbc.update("""
                insert into chess_users_2(id, username, signature, total_points, is_bot)
                select ?, ?, null, 0, true
                where not exists(select 1 from chess_users_2 where username=? and is_bot=true)
            """.trimIndent(), UUID.randomUUID(), name, name)
        }
    }

    @AfterEach
    fun finishFixturesAndCloseSockets() {
        sockets.forEach { it.socket.abort() }
        users.forEach { jdbc.update("delete from user_room_candidates_2 where user_id=?", it) }
        rooms.forEach {
            jdbc.update("update chess_rooms_2 set win_type='DRAW', winner_side=null where id=? and win_type is null", it)
        }
    }

    @Test
    fun `legacy signature and auth code keep existing UUID profile points and signature`() {
        val signature = "yandex-${UUID.randomUUID()}"
        val id = legacyUser(signature = signature, points = 317)
        val original = playerRow(id)
        val auth = request("POST", "/api/auth/signature", mapOf("signature" to signature, "playerName" to "Changed SDK name"))
        assertEquals(id.toString(), auth.path("backendUserId").asText())
        assertEquals(original["username"], auth.path("playerName").asText())
        val byCode = request("POST", "/api/auth/code/auth?authCode=${original["auth_code"]}")
        assertEquals(id.toString(), byCode.path("backendUserId").asText())
        val history = request("GET", "/history?signature=$signature")
        assertEquals(317, history.path("points").asInt())
        assertEquals(signature, history.path("signature").asText())
        assertEquals(original, playerRow(id))
        assertEquals(0L, count("auth_identities", "user_id", id))
    }

    @Test
    fun `legacy anonymous players and backend ID priority continue working`() {
        val first = request("POST", "/api/auth/signature", mapOf("signature" to null, "playerName" to "Guest A"))
        val second = request("POST", "/api/auth/signature", mapOf("signature" to null, "playerName" to "Guest B"))
        val id = UUID.fromString(first.path("backendUserId").asText()).also(users::add)
        users.add(UUID.fromString(second.path("backendUserId").asText()))
        assertNotEquals(first.path("backendUserId"), second.path("backendUserId"))
        val unrelatedSignature = "other-${UUID.randomUUID()}"
        val other = legacyUser(signature = unrelatedSignature)
        val saved = request("POST", "/history/save", historyBody(id, signature = unrelatedSignature, type = "BOT"))
        val room = UUID.fromString(saved.path("roomId").asText()).also(rooms::add)
        assertEquals(id.toString(), saved.path("backendUserId").asText())
        assertEquals(id, jdbc.queryForObject("select user_1 from chess_rooms_2 where id=?", UUID::class.java, room))
        assertNull(playerRow(id)["signature"])
        assertEquals(unrelatedSignature, playerRow(other)["signature"])
        assertEquals(1L, count("game_history", "user_id", id))
    }

    @Test
    fun `guest resume is stable wrong keys create no orphan and logout only revokes the session`() {
        val guest = guest()
        val id = guest.id()
        assertTrue(guest.path("guestKey").asText().matches(Regex("[A-Za-z0-9_-]{43}")))
        assertTrue(Instant.parse(guest.path("sessionExpiresAt").asText()).isAfter(Instant.now()))
        assertFalse(guest.path("profile").path("playerName").asText().isBlank())
        assertEquals(false, playerRow(id)["legacy_access_allowed"])
        val guestHash = jdbc.queryForObject("select secret_hash from auth_credentials where user_id=?", String::class.java, id)!!
        val tokenHash = jdbc.queryForObject("select token_hash from auth_sessions where id=?", String::class.java,
            UUID.fromString(guest.path("authSessionId").asText()))!!
        assertNotEquals(guest.path("guestKey").asText(), guestHash)
        assertNotEquals(guest.token(), tokenHash)
        assertTrue(guestHash.matches(Regex("[a-f0-9]{64}")))
        assertTrue(tokenHash.matches(Regex("[a-f0-9]{64}")))
        val before = count("chess_users_2")
        val bad = request("POST", "/api/v1/auth/guest/resume", mapOf("guestKey" to "x".repeat(43)), expected = 401)
        assertEquals("GUEST_KEY_INVALID", bad.path("code").asText())
        assertEquals(before, count("chess_users_2"))
        val resumed = request("POST", "/api/v1/auth/guest/resume", mapOf("guestKey" to guest.path("guestKey").asText()))
        assertEquals(id.toString(), resumed.path("backendUserId").asText())
        assertNotEquals(guest.token(), resumed.token())
        val me = request("GET", "/api/v1/auth/me", token = resumed.token())
        assertFalse(me.has("sessionToken"))
        assertFalse(me.has("guestKey"))
        request("POST", "/api/v1/auth/logout", token = resumed.token(), expected = 204)
        request("GET", "/api/v1/auth/me", token = resumed.token(), expected = 401)
        request("GET", "/api/v1/auth/me", token = guest.token())
        request("POST", "/api/v1/auth/guest/resume", mapOf("guestKey" to guest.path("guestKey").asText()))
    }

    @Test
    fun `expired or invalid bearer cannot fall back to a legacy profile`() {
        val legacy = legacyUser()
        val guest = guest()
        jdbc.update("update auth_sessions set created_at=now()-interval '2 hours', expires_at=now()-interval '1 second' where id=?",
            UUID.fromString(guest.path("authSessionId").asText()))
        listOf(guest.token(), "invalid-token").forEach { token ->
            request("PUT", "/user/name", mapOf("backendUserId" to legacy, "newName" to "Intruder"), token, 401)
            request("GET", "/api/v1/auth/me", token = token, expected = 401)
        }
        assertNotEquals("Intruder", playerRow(legacy)["username"])
        val resumed = request("POST", "/api/v1/auth/guest/resume", mapOf("guestKey" to guest.path("guestKey").asText()))
        assertEquals(guest.id(), resumed.id())
        request("GET", "/api/v1/auth/me", token = resumed.token())
    }

    @Test
    fun `exchange requires explicit creation and link preserves guest progress with lost reply recovery`() {
        val proof = UUID.randomUUID().toString()
        val before = count("chess_users_2")
        val missing = request("POST", "/api/v1/auth/exchange", mapOf("provider" to "test", "proof" to proof), expected = 404)
        assertEquals("IDENTITY_NOT_FOUND", missing.path("code").asText())
        request("POST", "/api/v1/auth/exchange", mapOf("provider" to "unknown", "proof" to proof), expected = 400)
        request("POST", "/api/v1/auth/exchange", mapOf("provider" to "test", "proof" to "invalid"), expected = 401)
        assertEquals(before, count("chess_users_2"))
        val account = request("POST", "/api/v1/auth/exchange", mapOf(
            "provider" to "test", "proof" to "account-$proof", "createIfMissing" to true))
        users.add(account.id())
        assertEquals("test", account.path("identityProfile").path("provider").asText())
        assertFalse(account.has("guestKey"))
        assertEquals(false, playerRow(account.id())["legacy_access_allowed"])
        val guest = guest()
        val secondGuestSession = request("POST", "/api/v1/auth/guest/resume", mapOf("guestKey" to guest.path("guestKey").asText()))
        jdbc.update("update chess_users_2 set total_points=97 where id=?", guest.id())
        val historyRoom = room(guest.id(), legacyUser())
        jdbc.update("""
            insert into game_history(user_id, room_id, game_type, match_number, user_game_status, favourite, created_at, points)
            values (?, ?, 'ONLINE', 4, 'WIN', true, now(), 23)
        """.trimIndent(), guest.id(), historyRoom)
        val historyBefore = jdbc.queryForMap("select * from game_history where user_id=? and room_id=?", guest.id(), historyRoom)
        val linked = request("POST", "/api/v1/auth/identities/link", mapOf("provider" to "test", "proof" to proof), guest.token())
        assertEquals(guest.id(), linked.id())
        assertEquals(97, linked.path("profile").path("totalPoints").asInt())
        request("GET", "/api/v1/auth/me", token = guest.token(), expected = 401)
        request("GET", "/api/v1/auth/me", token = secondGuestSession.token(), expected = 401)
        request("POST", "/api/v1/auth/guest/resume", mapOf("guestKey" to guest.path("guestKey").asText()), expected = 401)
        // The client may have lost the successful link response and still only have a fresh provider proof.
        val recovered = request("POST", "/api/v1/auth/exchange", mapOf("provider" to "test", "proof" to proof, "createIfMissing" to false))
        assertEquals(guest.id(), recovered.id())
        assertEquals(97, recovered.path("profile").path("totalPoints").asInt())
        assertEquals(1L, count("auth_identities", "user_id", guest.id()))
        assertEquals(historyBefore, jdbc.queryForMap("select * from game_history where user_id=? and room_id=?", guest.id(), historyRoom))
        assertEquals(guest.id(), jdbc.queryForObject("select user_1 from chess_rooms_2 where id=?", UUID::class.java, historyRoom))
        val other = guest()
        val conflict = request("POST", "/api/v1/auth/identities/link", mapOf("provider" to "test", "proof" to proof), other.token(), 409)
        assertEquals("IDENTITY_ALREADY_LINKED", conflict.path("code").asText())
        assertEquals(other.id().toString(), request("GET", "/api/v1/auth/me", token = other.token()).path("backendUserId").asText())
        assertEquals(0L, count("auth_identities", "user_id", other.id()))
    }

    @Test
    fun `concurrent exchanges of one proof create exactly one player and identity`() {
        val proof = UUID.randomUUID().toString()
        val before = count("chess_users_2")
        val start = CompletableFuture<Unit>()
        val requests = (1..2).map {
            CompletableFuture.supplyAsync {
                start.get(5, TimeUnit.SECONDS)
                request("POST", "/api/v1/auth/exchange", mapOf(
                    "provider" to "test", "proof" to proof, "createIfMissing" to true))
            }
        }
        start.complete(Unit)
        val responses = requests.map { it.get(15, TimeUnit.SECONDS) }
        users.add(responses.first().id())
        assertEquals(responses.first().id(), responses.last().id())
        assertNotEquals(responses.first().token(), responses.last().token())
        assertEquals(before + 1, count("chess_users_2"))
        assertEquals(1L, jdbc.queryForObject("""
            select count(*) from auth_identities where provider='test' and namespace='integration' and subject=?
        """.trimIndent(), Long::class.java, proof))
        assertEquals(1L, count("auth_identities", "user_id", responses.first().id()))
    }

    @Test
    fun `link waits for matchmaking and unfinished match without revoking the guest`() {
        val guest = guest()
        val proof = mapOf("provider" to "test", "proof" to UUID.randomUUID().toString())
        jdbc.update("""
            insert into user_room_candidates_2(id, user_id, user_side, created_at, active_until)
            values (?, ?, 'WHITE', now(), now()+interval '5 minutes')
        """.trimIndent(), UUID.randomUUID(), guest.id())
        assertEquals("ACTIVE_MATCH", request("POST", "/api/v1/auth/identities/link", proof, guest.token(), 409).path("code").asText())
        jdbc.update("delete from user_room_candidates_2 where user_id=?", guest.id())
        val room = room(guest.id(), legacyUser(), finished = false)
        assertEquals("ACTIVE_MATCH", request("POST", "/api/v1/auth/identities/link", proof, guest.token(), 409).path("code").asText())
        request("GET", "/api/v1/auth/me", token = guest.token())
        assertEquals(0L, count("auth_identities", "user_id", guest.id()))
        jdbc.update("update chess_rooms_2 set win_type='DRAW' where id=?", room)
        assertEquals(guest.id(), request("POST", "/api/v1/auth/identities/link", proof, guest.token()).id())
    }

    @Test
    fun `modern HTTP enforces profile room and side ownership while public replay stays available`() {
        val owner = guest()
        val other = guest()
        val outsider = guest()
        val room = room(owner.id(), other.id())
        jdbc.update("""
            insert into game_history(user_id, room_id, game_type, match_number, user_game_status, favourite, created_at, points)
            values (?, ?, 'ONLINE', 1, 'WIN', false, now(), 30)
        """.trimIndent(), owner.id(), room)
        request("GET", "/history?backendUserId=${owner.id()}", expected = 403)
        request("GET", "/history?backendUserId=${owner.id()}", token = other.token(), expected = 403)
        request("PUT", "/user/name", mapOf("backendUserId" to owner.id(), "newName" to "Intruder"), other.token(), 403)
        request("PUT", "/favourite", mapOf("backendUserId" to owner.id(), "room" to room), other.token(), 403)
        request("POST", "/history/save", historyBody(owner.id(), room, side = "WHITE"), other.token(), 403)
        request("POST", "/history/save", historyBody(owner.id(), room, side = "BLACK"), owner.token(), 403)
        request("POST", "/history/save", historyBody(outsider.id(), room, side = "WHITE"), outsider.token(), 403)
        request("POST", "/api/auth/code/generate?backendUserId=${owner.id()}", expected = 403)
        request("PUT", "/user/name", mapOf("backendUserId" to owner.id(), "newName" to "My name"), owner.token())
        request("PUT", "/favourite", mapOf("backendUserId" to owner.id(), "room" to room), owner.token())
        assertEquals("My name", playerRow(owner.id())["username"])
        assertEquals(true, jdbc.queryForObject("select favourite from game_history where user_id=? and room_id=?",
            Boolean::class.java, owner.id(), room))
        assertEquals(owner.id().toString(), request("GET", "/history?backendUserId=${owner.id()}", token = owner.token()).path("backendUserId").asText())
        assertEquals(owner.id().toString(), request("POST", "/history/save", historyBody(owner.id(), room), owner.token()).path("backendUserId").asText())
        assertEquals(room.toString(), request("GET", "/history/room?roomId=$room").path("room").asText())
    }

    @Test
    fun `modern and legacy sockets match exchange moves and reconnect with same owner`() {
        val modern = guest()
        val legacy = legacyUser()
        val white = socket().authenticate(modern)
        val black = socket()
        white.send(search(modern.id(), "WHITE"))
        black.send(search(legacy, "BLACK"))
        val found = white.await("ROOM_FOUND")
        val room = UUID.fromString(found.path("room").asText()).also(rooms::add)
        assertEquals(room.toString(), black.await("ROOM_FOUND").path("room").asText())
        assertEquals("WHITE", found.path("playerSide").asText())
        val firstMove = move(modern.id(), room, "WHITE", "E2E4", 1)
        white.send(firstMove)
        val accepted = white.await("CHESS_MOVE_ACCEPTED")
        assertEquals(accepted.path("moveId"), black.await("CHESS_MOVE_ACCEPTED").path("moveId"))
        white.socket.abort()
        val reconnected = socket().authenticate(modern)
        reconnected.send(mapOf("messageType" to "WS_RETRY", "backendUserId" to modern.id(), "roomId" to room))
        val restored = reconnected.await("NEW_WS")
        assertEquals(room.toString(), restored.path("room").asText())
        assertEquals(1, restored.path("turns").size())
        reconnected.send(firstMove)
        assertEquals(accepted.path("turnServerTimeMs"), reconnected.await("CHESS_MOVE_ACCEPTED").path("turnServerTimeMs"))
        black.await("CHESS_MOVE_ACCEPTED")
        assertEquals(1L, count("chess_moves", "room_id", room))
        // A white authenticated player cannot submit the black opponent's next move.
        reconnected.send(move(modern.id(), room, "BLACK", "E7E5", 2))
        reconnected.send(mapOf("messageType" to "PING", "backendUserId" to modern.id(), "roomId" to room))
        reconnected.await("PONG") // Ordered processing proves the preceding rejected move has been handled.
        assertEquals(1L, count("chess_moves", "room_id", room))
        black.send(move(legacy, room, "BLACK", "E7E5", 2))
        assertEquals(2, black.await("CHESS_MOVE_ACCEPTED").path("ply").asInt())
        assertEquals(2, reconnected.await("CHESS_MOVE_ACCEPTED").path("ply").asInt())
        black.send(mapOf("messageType" to "MATCH_FINISHED", "room" to room, "winnerSide" to "BLACK", "finishType" to "SURRENDER"))
        assertEquals("SURRENDER", black.await("MATCH_FINISHED_WS").path("finishType").asText())
    }

    @Test
    fun `legacy websocket still accepts signature only and first anonymous visit`() {
        val signature = "yandex-ws-${UUID.randomUUID()}"
        val existing = legacyUser(signature = signature, points = 71)
        val original = playerRow(existing)
        val white = socket()
        white.send(search(null, "WHITE") + ("signature" to signature))
        assertEquals(existing.toString(), white.await("NEW_WS").path("backendUserId").asText())
        val black = socket()
        black.send(search(null, "BLACK"))
        val anonymous = UUID.fromString(black.await("NEW_WS").path("backendUserId").asText()).also(users::add)
        assertNotEquals(existing, anonymous)
        val room = UUID.fromString(white.await("ROOM_FOUND").path("room").asText()).also(rooms::add)
        assertEquals(room.toString(), black.await("ROOM_FOUND").path("room").asText())
        assertNull(playerRow(anonymous)["signature"])
        assertEquals(true, playerRow(anonymous)["legacy_access_allowed"])
        white.send(mapOf("messageType" to "MATCH_FINISHED", "room" to room, "winnerSide" to "BLACK", "finishType" to "SURRENDER"))
        white.await("MATCH_FINISHED_WS")
        black.await("MATCH_FINISHED_WS")
        assertEquals(original, playerRow(existing))
    }

    @Test
    fun `websocket authentication cannot downgrade or impersonate protected users`() {
        val modern = guest()
        val unauthenticated = socket()
        unauthenticated.send(search(modern.id(), "WHITE"))
        unauthenticated.await("AUTH_ERROR")
        unauthenticated.awaitClosed()
        assertEquals(0L, count("user_room_candidates_2", "user_id", modern.id()))
        val invalid = socket()
        invalid.send(mapOf("messageType" to "AUTH", "sessionToken" to "invalid"))
        invalid.await("AUTH_ERROR")
        invalid.awaitClosed()
        val valid = socket().authenticate(modern)
        valid.send(search(legacyUser(), "WHITE"))
        valid.await("AUTH_ERROR")
        valid.awaitClosed()
    }

    @Test
    fun `logout closes an authenticated websocket and expired sessions cannot reconnect`() {
        val modern = guest()
        val authenticated = socket().authenticate(modern)
        request("POST", "/api/v1/auth/logout", token = modern.token(), expected = 204)
        authenticated.awaitClosed()
        val revoked = socket()
        revoked.send(mapOf("messageType" to "AUTH", "sessionToken" to modern.token()))
        revoked.await("AUTH_ERROR")
        revoked.awaitClosed()
        val renewed = request("POST", "/api/v1/auth/guest/resume", mapOf("guestKey" to modern.path("guestKey").asText()))
        jdbc.update("update auth_sessions set created_at=now()-interval '2 hours', expires_at=now()-interval '1 second' where id=?",
            UUID.fromString(renewed.path("authSessionId").asText()))
        val expired = socket()
        expired.send(mapOf("messageType" to "AUTH", "sessionToken" to renewed.token()))
        expired.await("AUTH_ERROR")
        expired.awaitClosed()
    }

    private fun guest(): JsonNode = request("POST", "/api/v1/auth/guest/create").also { users.add(it.id()) }
    private fun JsonNode.id(): UUID = UUID.fromString(path("backendUserId").asText())
    private fun JsonNode.token(): String = path("sessionToken").asText()

    private fun request(method: String, path: String, body: Any? = null, token: String? = null, expected: Int = 200): JsonNode {
        val builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).timeout(Duration.ofSeconds(10))
        token?.let { builder.header("Authorization", "Bearer $it") }
        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else {
            builder.header("Content-Type", "application/json")
            HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))
        }
        val response = client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
        assertEquals(expected, response.statusCode(), "$method $path returned ${response.body()}")
        return if (response.body().isBlank()) mapper.createObjectNode() else mapper.readTree(response.body())
    }

    private fun legacyUser(signature: String? = null, points: Long = 0): UUID {
        val id = UUID.randomUUID().also(users::add)
        jdbc.update("insert into chess_users_2(id, username, signature, total_points, auth_code) values (?, ?, ?, ?, ?)",
            id, "Legacy-$id", signature, points, "code-$id")
        return id
    }

    private fun playerRow(id: UUID): Map<String, Any?> = jdbc.queryForMap("select * from chess_users_2 where id=?", id)
    private fun count(table: String): Long = jdbc.queryForObject("select count(*) from $table", Long::class.java)!!
    private fun count(table: String, column: String, id: UUID): Long =
        jdbc.queryForObject("select count(*) from $table where $column=?", Long::class.java, id)!!

    private fun room(white: UUID, black: UUID, finished: Boolean = true): UUID {
        val id = UUID.randomUUID().also(rooms::add)
        jdbc.update("""
            insert into chess_rooms_2(id, user_1, user_1_side, user_1_name, user_2, user_2_side, user_2_name,
                game_type, history, winner_side, win_type)
            values (?, ?, 'WHITE', 'White', ?, 'BLACK', 'Black', 'ONLINE', '1.e4 e5', ?, ?)
        """.trimIndent(), id, white, black, if (finished) "WHITE" else null, if (finished) "CHECKMATE" else null)
        return id
    }

    private fun historyBody(id: UUID?, room: UUID? = null, side: String = "WHITE", signature: String? = null,
                            type: String = "ONLINE") = mapOf(
        "backendUserId" to id, "signature" to signature, "room" to room, "userSide" to side,
        "username" to "Player", "opponentName" to "Opponent", "opponentType" to type,
        "history" to "1.e4 e5", "winnerSide" to "BLACK", "finishType" to "CHECKMATE"
    )

    private fun search(id: UUID?, side: String) = mapOf("messageType" to "REQUEST_FOR_ROOM", "backendUserId" to id,
        "signature" to null, "username" to "Player", "side" to side)
    private fun move(id: UUID, room: UUID, side: String, move: String, ply: Int) = mapOf(
        "messageType" to "CHESS_MOVE", "backendUserId" to id, "room" to room, "sideOfMove" to side,
        "move" to move, "moveId" to UUID.randomUUID(), "ply" to ply, "moveForHistory" to "1.e4"
    )

    private fun socket(): SocketClient = SocketClient().also {
        it.socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
            .buildAsync(URI.create("ws://127.0.0.1:$port/"), it).get(5, TimeUnit.SECONDS)
        sockets.add(it)
    }

    private inner class SocketClient : WebSocket.Listener {
        lateinit var socket: WebSocket
        private val messages = LinkedBlockingQueue<JsonNode>()
        private val partial = StringBuilder()
        private val closed = CompletableFuture<Int>()
        override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }
        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            partial.append(data)
            if (last) {
                messages.add(mapper.readTree(partial.toString()))
                partial.setLength(0)
            }
            webSocket.request(1)
            return null
        }
        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            closed.complete(statusCode)
            return null
        }
        override fun onError(webSocket: WebSocket, error: Throwable) { closed.completeExceptionally(error) }
        fun send(body: Any) { socket.sendText(mapper.writeValueAsString(body), true).get(5, TimeUnit.SECONDS) }
        fun authenticate(auth: JsonNode): SocketClient {
            send(mapOf("messageType" to "AUTH", "sessionToken" to auth.token()))
            assertEquals(auth.id().toString(), await("AUTH_OK").path("backendUserId").asText())
            return this
        }
        fun await(type: String): JsonNode {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            val seen = mutableListOf<String>()
            while (true) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) throw AssertionError("No $type; received types: $seen")
                val message = messages.poll(remaining, TimeUnit.NANOSECONDS)
                    ?: throw AssertionError("No $type; received types: $seen")
                val actual = message.path("messageType").asText()
                if (actual == type) return message
                seen.add(actual)
                if (actual == "AUTH_ERROR") throw AssertionError("Unexpected auth error: $message")
            }
        }
        fun awaitClosed() { assertEquals(1008, closed.get(5, TimeUnit.SECONDS)) }
    }
}
