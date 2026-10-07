package com.gamelaunch.frontend

import com.gamelaunch.frontend.data.db.dao.EmulatorMappingDao
import com.gamelaunch.frontend.data.db.entity.EmulatorMappingEntity
import com.gamelaunch.frontend.data.repository.EmulatorRepositoryImpl
import com.gamelaunch.frontend.domain.model.Platform
import com.gamelaunch.frontend.domain.platform.PlatformCatalog
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FollowCatalogCoresTest {

    private val dao: EmulatorMappingDao = mock()
    private val repo = EmulatorRepositoryImpl(dao, mock<PackageManagerHelper>())

    private fun catalog(core: String) = PlatformCatalog(
        revision = 1,
        platforms = listOf(
            Platform(id = "sgb", displayName = "Super Game Boy", scraperSystemId = 127,
                extensions = listOf(".gb"), folderNames = listOf("sgb"), defaultCoreForRetroArch = core)
        ),
        emulators = emptyList(), emulatorPriority = emptyMap(), launchSpecs = emptyMap()
    )

    private fun mapping(core: String?, retroArch: Boolean = true) = EmulatorMappingEntity(
        id = 4, platformId = "sgb", packageName = "com.retroarch.aarch64",
        isRetroArch = retroArch, retroArchCore = core
    )

    @Test fun `mapping on the old default core follows the catalog`() = runTest {
        whenever(dao.getMappingForPlatform("sgb")).thenReturn(mapping("mesen2_libretro.so"))

        val n = repo.followCatalogCores(catalog("mesen2_libretro.so"), catalog("mgba_libretro.so"))

        assertEquals(1, n)
        verify(dao).upsertMapping(mapping("mgba_libretro.so"))
    }

    @Test fun `a core the user picked is left alone`() = runTest {
        whenever(dao.getMappingForPlatform("sgb")).thenReturn(mapping("gambatte_libretro.so"))

        assertEquals(0, repo.followCatalogCores(catalog("mesen2_libretro.so"), catalog("mgba_libretro.so")))
        verify(dao, never()).upsertMapping(any())
    }

    @Test fun `standalone emulators and unchanged cores are untouched`() = runTest {
        whenever(dao.getMappingForPlatform("sgb")).thenReturn(mapping("mesen2_libretro.so", retroArch = false))
        assertEquals(0, repo.followCatalogCores(catalog("mesen2_libretro.so"), catalog("mgba_libretro.so")))

        whenever(dao.getMappingForPlatform("sgb")).thenReturn(mapping("mgba_libretro.so"))
        assertEquals(0, repo.followCatalogCores(catalog("mgba_libretro.so"), catalog("mgba_libretro.so")))
        verify(dao, never()).upsertMapping(any())
    }
}
