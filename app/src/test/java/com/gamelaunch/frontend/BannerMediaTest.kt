package com.gamelaunch.frontend

import android.content.Context
import com.gamelaunch.frontend.data.db.dao.GameMediaDao
import com.gamelaunch.frontend.data.db.entity.GameMediaEntity
import com.gamelaunch.frontend.data.preferences.AppDataStore
import com.gamelaunch.frontend.data.repository.MediaRepositoryImpl
import com.gamelaunch.frontend.domain.model.GameMedia
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*
import java.io.File
import java.util.Base64

class BannerMediaTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `banner replaces enlarged cover in detail but leaves the cover intact`() {
        val media = GameMedia(gameId = 3, boxArtLocalPath = "/icons/3.png", bannerLocalPath = "/banners/3.png")
        assertEquals("/banners/3.png", media.effectiveDetailArtwork)
        assertTrue(media.detailArtworkIsBanner)
        assertEquals("/icons/3.png", media.effectiveBoxArt)
    }

    @Test fun `local and remote screenshots take priority over the banner`() {
        val media = GameMedia(gameId = 3, bannerLocalPath = "/banners/3.png",
            screenshotRemoteUrl = "https://example.com/shot.png")
        assertEquals(media.screenshotRemoteUrl, media.effectiveDetailArtwork)
        assertFalse(media.detailArtworkIsBanner)
        assertEquals("/shots/3.png", media.copy(screenshotLocalPath = "/shots/3.png").effectiveDetailArtwork)
    }

    @Test fun `games without banners retain the existing cover fallback`() {
        val media = GameMedia(gameId = 3, boxArtRemoteUrl = "https://example.com/cover.png")
        assertEquals(media.effectiveBoxArt, media.effectiveDetailArtwork)
        assertFalse(media.detailArtworkIsBanner)
        assertNull(GameMedia(gameId = 3).effectiveDetailArtwork)
    }

    @Test fun `rendered banner preserves existing media through the repository`() = runTest {
        val context: Context = mock { on { filesDir } doReturn temp.root }
        val store: AppDataStore = mock { on { mediaStoragePath } doReturn flowOf("") }
        val dao: GameMediaDao = mock()
        val previous = GameMediaEntity(id = 9, gameId = 3, boxArtLocalPath = "/covers/3.png",
            screenshotLocalPath = "/shots/3.png", videoRemoteUrl = "https://example.com/3.mp4")
        whenever(dao.getMediaForGame(3)).thenReturn(previous)
        val repository = MediaRepositoryImpl(context, dao, store, OkHttpClient())
        val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF2kAAAAASUVORK5CYII=")

        val path = requireNotNull(repository.saveEmbeddedBanner(3, png))
        assertEquals(File(temp.root, "media/banners").absolutePath, File(path).parent)
        assertArrayEquals(png, File(path).readBytes())
        val saved = argumentCaptor<GameMediaEntity>()
        verify(dao).upsertMedia(saved.capture())
        assertEquals(previous.copy(bannerLocalPath = path), saved.firstValue)
        whenever(dao.getMediaForGame(3)).thenReturn(saved.firstValue)
        assertEquals(path, repository.getMediaForGame(3)?.bannerLocalPath)

        // A later scrape must not discard the already-cached banner.
        repository.upsertMedia(GameMedia(gameId = 3, wheelLogoLocalPath = "/wheels/3.png"))
        verify(dao, times(2)).upsertMedia(saved.capture())
        assertEquals(path, saved.lastValue.bannerLocalPath)
        repository.deleteMediaForGame(3)
        assertFalse(File(path).exists())
    }

    @Test fun `device-rendered banners use fresh cache keys and prune obsolete generated files`() = runTest {
        val context: Context = mock { on { filesDir } doReturn temp.root }
        val store: AppDataStore = mock { on { mediaStoragePath } doReturn flowOf("") }
        val dao: GameMediaDao = mock()
        val previous = GameMediaEntity(id = 9, gameId = 3, boxArtLocalPath = "/icons/3.png")
        whenever(dao.getMediaForGame(3)).thenReturn(previous)
        val repository = MediaRepositoryImpl(context, dao, store, OkHttpClient())
        val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF2kAAAAASUVORK5CYII=")

        val first = repository.saveEmbeddedBanner(3, png)!!
        val second = repository.saveEmbeddedBanner(3, png)!!

        assertNotEquals(first, second)
        assertFalse(File(first).exists())
        assertArrayEquals(png, File(second).readBytes())
        val saved = argumentCaptor<GameMediaEntity>()
        verify(dao, times(2)).upsertMedia(saved.capture())
        assertEquals(previous.copy(bannerLocalPath = second), saved.lastValue)
        repository.deleteMediaForGame(3)
        assertFalse(File(second).exists())
    }
}
