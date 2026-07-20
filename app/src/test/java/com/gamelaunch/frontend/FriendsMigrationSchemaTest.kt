package com.gamelaunch.frontend

import com.gamelaunch.frontend.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Guards MIGRATION_2_3 against drift from FriendEntity. The v3 schema JSON is frozen, so the DDL the
 * migration runs must stay byte-identical to what Room expects — otherwise Room's schema validation
 * fails at runtime (or the destructive fallback silently wipes the user's library). If FriendEntity
 * changes, that's a new schema version + migration, not an edit to v3.
 *
 * Reads the generated schema as raw text (Android's org.json is a non-functional stub in JVM unit
 * tests, so we can't parse it as JSON here). The full on-device migration is covered by the
 * MigrationTestHelper androidTest.
 */
class FriendsMigrationSchemaTest {

    @Test fun `migration DDL matches frozen v3 schema`() {
        val schemaText = findSchemaText(3)
        // Pull every "createSql": "..." value, then pick the one for the friends table (unique col).
        val createSqls = Regex("\"createSql\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .findAll(schemaText).map { it.groupValues[1] }.toList()
        val friendsSql = createSqls.single { it.contains("device_id") }
            .replace("\\u0060", "`") // some Room versions unicode-escape backticks in the JSON
            .replace("\${TABLE_NAME}", "friends")
        assertEquals(friendsSql, DatabaseModule.FRIENDS_CREATE_SQL)
    }

    private fun findSchemaText(version: Int): String {
        // Unit tests may run with the working dir at the module or repo root; try both.
        val root = listOf("schemas", "app/schemas").map { File(it) }.firstOrNull { it.isDirectory }
            ?: error("schema dir not found from ${File(".").absolutePath}")
        val json = root.walkTopDown().firstOrNull { it.name == "$version.json" }
            ?: error("$version.json not found under ${root.absolutePath}")
        return json.readText()
    }
}
