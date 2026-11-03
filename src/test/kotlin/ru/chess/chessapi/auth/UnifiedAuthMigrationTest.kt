package ru.chess.chessapi.auth

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/** Uses its own empty PostgreSQL database; never drops or resets an existing schema. */
@EnabledIfEnvironmentVariable(named = "AUTH_MIGRATION_TEST_DATABASE_URL", matches = ".+")
class UnifiedAuthMigrationTest {
    @Test
    fun `auth migration preserves legacy players history and applied changesets`() {
        DriverManager.getConnection(
            System.getenv("AUTH_MIGRATION_TEST_DATABASE_URL"),
            System.getenv("AUTH_TEST_DATABASE_USERNAME"),
            System.getenv("AUTH_TEST_DATABASE_PASSWORD")
        ).use { connection ->
            assertNull(connection.scalar("select to_regclass('public.databasechangelog')"),
                "AUTH_MIGRATION_TEST_DATABASE_URL must point to an empty disposable database")
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))
            val resources = ClassLoaderResourceAccessor()
            val oldMigrations = listOf(
                "V1.0__create-first-tables.xml",
                "V1.1__create_room_and_user_room_candidate_tables.xml",
                "V1.3__create-v_2-tables.xml",
                "V1.4__add_auth_code_field_to_user2_table.xml",
                "V1.6__create_game_history_table.xml",
                "V1.7__add_points_to_game_history_table.xml",
                "V1.8__alter_rating_in_chess_users_2_table.xml",
                "V1.9__persist_accepted_moves.xml"
            )
            oldMigrations.forEach {
                Liquibase("db/changelog/$it", resources, database).update(Contexts(), LabelExpression())
            }

            val user = UUID.randomUUID()
            val opponent = UUID.randomUUID()
            val room = UUID.randomUUID()
            connection.createStatement().use { statement ->
                statement.executeUpdate("""
                    insert into chess_users_2(id, username, signature, auth_code, total_points)
                    values ('$user', 'Existing Yandex player', 'legacy-signature', 'CODE123', 317),
                           ('$opponent', 'Existing anonymous player', null, null, 29)
                """.trimIndent())
                statement.executeUpdate("""
                    insert into chess_rooms_2(id, user_1, user_1_side, user_1_name,
                        user_2, user_2_side, user_2_name, game_type, history, winner_side, win_type)
                    values ('$room', '$user', 'WHITE', 'Existing Yandex player',
                        '$opponent', 'BLACK', 'Existing anonymous player', 'ONLINE', '1.e4 e5', 'WHITE', 'CHECKMATE')
                """.trimIndent())
                statement.executeUpdate("""
                    insert into game_history(user_id, room_id, game_type, match_number,
                        user_game_status, favourite, created_at, points)
                    values ('$user', '$room', 'ONLINE', 7, 'WIN', true, timestamp '2026-01-02 03:04:05', 30)
                """.trimIndent())
            }
            val users = connection.rows("""
                select id, username, signature, serial_number, total_points, is_bot, created_at, auth_code
                from chess_users_2 order by id
            """.trimIndent())
            val rooms = connection.rows("select * from chess_rooms_2 order by id")
            val history = connection.rows("select * from game_history order by user_id, room_id")
            val changesets = connection.rows("select id, author, filename, md5sum from databasechangelog order by orderexecuted")
            val columns = connection.rows("""
                select column_name, data_type, is_nullable, column_default
                from information_schema.columns
                where table_schema='public' and table_name='chess_users_2'
                order by ordinal_position
            """.trimIndent())

            Liquibase("db/changelog/db.changelog-master.xml", resources, database)
                .update(Contexts(), LabelExpression())

            assertEquals(users, connection.rows("""
                select id, username, signature, serial_number, total_points, is_bot, created_at, auth_code
                from chess_users_2 order by id
            """.trimIndent()))
            assertEquals(rooms, connection.rows("select * from chess_rooms_2 order by id"))
            assertEquals(history, connection.rows("select * from game_history order by user_id, room_id"))
            assertEquals(changesets, connection.rows("""
                select id, author, filename, md5sum from databasechangelog
                order by orderexecuted limit ${changesets.size}
            """.trimIndent()))
            assertEquals(columns, connection.rows("""
                select column_name, data_type, is_nullable, column_default
                from information_schema.columns
                where table_schema='public' and table_name='chess_users_2'
                  and column_name <> 'legacy_access_allowed'
                order by ordinal_position
            """.trimIndent()))
            assertEquals(2L, connection.scalar("select count(*) from chess_users_2 where legacy_access_allowed = true"))
            listOf("auth_identities", "auth_credentials", "auth_sessions").forEach { table ->
                assertNotNull(connection.scalar("select to_regclass('public.$table')"))
                assertEquals(0L, connection.scalar("select count(*) from $table"),
                    "Existing legacy players must not acquire fabricated verified identities or credentials")
            }
        }
    }

    private fun Connection.scalar(sql: String): Any? =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                result.getObject(1)
            }
        }

    private fun Connection.rows(sql: String): List<List<Any?>> =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                buildList {
                    while (result.next()) add((1..result.metaData.columnCount).map(result::getObject))
                }
            }
        }
}
