package com.gamelaunch.frontend

import androidx.sqlite.db.SupportSQLiteDatabase
import com.gamelaunch.frontend.di.DatabaseModule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.File

class BannerMigrationSchemaTest {
    private fun schema(version: Int): JSONObject {
        val root = listOf("schemas", "app/schemas").map(::File).first { it.isDirectory }
        val file = root.walkTopDown().first { it.name == "$version.json" }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    @Test fun `migration only adds nullable banner column while preserving existing tables`() {
        val db: SupportSQLiteDatabase = mock()
        DatabaseModule.MIGRATION_6_7.migrate(db)
        verify(db).execSQL("ALTER TABLE game_media ADD COLUMN banner_local TEXT")
        val old = schema(6).getJSONArray("entities")
        val fresh = schema(7).getJSONArray("entities")
        assertEquals(old.length(), fresh.length())
        for (i in 0 until old.length()) {
            val before = old.getJSONObject(i)
            val after = fresh.getJSONObject(i)
            assertEquals(before.getString("tableName"), after.getString("tableName"))
            if (before.getString("tableName") == "game_media") {
                assertEquals(before.getString("createSql"), after.getString("createSql").replace("`banner_local` TEXT, ", ""))
                val fields = after.getJSONArray("fields")
                val field = fields.getJSONObject(fields.length() - 1)
                assertEquals("banner_local", field.getString("columnName"))
                assertEquals("TEXT", field.getString("affinity"))
                assertFalse(field.getBoolean("notNull"))
            } else {
                assertEquals(before.toString(), after.toString())
            }
        }
    }
}
