package com.gamelaunch.frontend

import com.gamelaunch.frontend.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Guards MIGRATION_5_6. The SQL it runs must match Room's frozen v6 schema exactly, or Room's
 * validation fails on upgrade and the destructive fallback wipes the user's library.
 * Reads the schema JSON as text (Android's org.json is a stub in JVM unit tests).
 */
class PlaySessionsMigrationSchemaTest {

    private fun schema(version: Int): String {
        val root = listOf("schemas", "app/schemas").map { File(it) }.firstOrNull { it.isDirectory }
            ?: error("schema dir not found from ${File(".").absolutePath}")
        return root.walkTopDown().first { it.name == "$version.json" }.readText()
    }

    private fun createSqls(schemaText: String): List<String> =
        Regex("\"createSql\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(schemaText)
            .map { it.groupValues[1].replace("\\u0060", "`").replace("\\\"", "\"") }
            .toList()

    @Test fun `migration SQL matches the frozen v6 schema`() {
        val v6 = createSqls(schema(6)).map { it.replace("\${TABLE_NAME}", "play_sessions") }
        assertEquals(v6.single { it.contains("segment_started_at") }, DatabaseModule.PLAY_SESSIONS_CREATE_SQL)
        assertEquals(v6.single { it.contains("index_play_sessions_game_id") }, DatabaseModule.PLAY_SESSIONS_GAME_INDEX_SQL)
        assertEquals(v6.single { it.contains("index_play_sessions_started_at") }, DatabaseModule.PLAY_SESSIONS_STARTED_INDEX_SQL)
    }

    @Test fun `v6 only adds play_sessions on top of v5`() {
        // Every v5 table/index statement must be unchanged in v6, so the migration needs nothing else.
        val v5 = createSqls(schema(5)).toSet()
        val v6 = createSqls(schema(6)).toSet()
        assertEquals(emptySet<String>(), v5 - v6)
        assertEquals(3, (v6 - v5).size)
    }
}
