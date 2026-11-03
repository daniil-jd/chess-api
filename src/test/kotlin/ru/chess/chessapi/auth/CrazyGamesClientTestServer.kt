package ru.chess.chessapi.auth

import org.springframework.boot.ApplicationRunner
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import ru.chess.chessapi.ChessApiApplication
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID

/** Test-classpath launcher for a disposable client E2E backend; the real JWT verifier remains in use. */
object CrazyGamesClientTestServer {
    @JvmStatic
    fun main(args: Array<String>) {
        requireTestLaunch()
        SpringApplicationBuilder(ChessApiApplication::class.java, ClientTestConfiguration::class.java)
            .run(*args)
    }

    private fun requireTestLaunch() {
        check(System.getenv("AUTH_CLIENT_E2E") == "true") {
            "The client E2E backend requires AUTH_CLIENT_E2E=true and a disposable database"
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class ClientTestConfiguration {
        @Bean
        @Primary
        fun clientTestCrazyGamesPublicKey(): CrazyGamesPublicKeyProvider {
            CrazyGamesClientTestServer.requireTestLaunch()
            val file = System.getenv("AUTH_E2E_PUBLIC_KEY_FILE")
            require(!file.isNullOrBlank()) { "AUTH_E2E_PUBLIC_KEY_FILE is required" }
            val pem = Files.readString(Path.of(file)).trim()
            require(pem.startsWith("-----BEGIN PUBLIC KEY-----") && pem.endsWith("-----END PUBLIC KEY-----")) {
                "The client E2E key must be an X.509 public PEM"
            }
            val encoded = pem.removePrefix("-----BEGIN PUBLIC KEY-----")
                .removeSuffix("-----END PUBLIC KEY-----").replace(Regex("\\s"), "")
            val key = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded))) as RSAPublicKey
            require(key.modulus.bitLength() >= 2048) { "The client E2E RSA key must be at least 2048 bits" }
            return object : CrazyGamesPublicKeyProvider {
                override fun currentKey(): RSAPublicKey = key
                override fun refreshKey(rejectedKey: RSAPublicKey): RSAPublicKey = key
            }
        }

        @Bean
        fun seedClientTestBots(jdbc: JdbcTemplate) = ApplicationRunner {
            CrazyGamesClientTestServer.requireTestLaunch()
            listOf("white_bot", "black_bot").forEach { name ->
                jdbc.update("""
                    insert into chess_users_2(id, username, signature, total_points, is_bot)
                    select ?, ?, null, 0, true
                    where not exists(select 1 from chess_users_2 where username=? and is_bot=true)
                """.trimIndent(), UUID.randomUUID(), name, name)
            }
        }
    }
}
