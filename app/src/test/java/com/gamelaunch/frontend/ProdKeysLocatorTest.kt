package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.usecase.ProdKeysLocator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProdKeysLocatorTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `finds a non-empty prod keys file at any nested location`() {
        val keys = File(tmp.root, "any/emulator/layout/keys/Prod.Keys").apply {
            parentFile!!.mkdirs()
            writeText("dummy key data")
        }

        assertEquals(listOf(keys), ProdKeysLocator.findAllInRoots(listOf(tmp.root)).toList())
    }

    @Test fun `rejects an empty key file`() {
        val empty = File(tmp.root, "prod.keys").apply { createNewFile() }

        assertEquals(emptyList<File>(), ProdKeysLocator.findAllInRoots(listOf(tmp.root)).toList())
    }
}
