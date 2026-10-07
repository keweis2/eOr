package com.gamelaunch.frontend

import com.gamelaunch.frontend.data.catalog.CatalogRepository
import com.gamelaunch.frontend.data.catalog.CatalogRepository.RefreshResult
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CatalogRepositoryTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private val server = MockWebServer()

    private fun catalog(revision: Int, minApp: Int = 1) =
        """{"schemaVersion":1,"revision":$revision,"minAppVersionCode":$minApp,"platforms":[
           {"id":"vectrex","displayName":"Vectrex","scraperSystemId":102,"extensions":[".vec"],
            "folderNames":["vectrex"]}]}"""

    private fun repo() = CatalogRepository(
        dir = tmpFolder.root,
        client = OkHttpClient(),
        url = server.url("/catalog.json").toString(),
        appVersionCode = 48,
        log = {}
    )

    @Before fun setUp() = server.start()

    @After fun tearDown() {
        server.shutdown()
        PlatformDefinitions.reset()
    }

    @Test fun `stores the ETag and revalidates with it`() = runTest {
        server.enqueue(MockResponse().setBody(catalog(2)).setHeader("ETag", "W/\"abc\""))
        server.enqueue(MockResponse().setResponseCode(304))

        assertEquals(RefreshResult.Updated(2), repo().refresh(force = true))
        assertNull(server.takeRequest().getHeader("If-None-Match"))

        assertEquals(RefreshResult.Unchanged, repo().refresh(force = true))
        assertEquals("W/\"abc\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test fun `a successful check is throttled for a day`() = runTest {
        server.enqueue(MockResponse().setBody(catalog(2)))
        repo().refresh(force = true)

        assertEquals(RefreshResult.Skipped, repo().refresh())
        assertEquals(1, server.requestCount)
    }

    @Test fun `a failed check is retried on the next launch`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(catalog(2)))

        assertEquals(RefreshResult.Failed("HTTP 500"), repo().refresh())
        assertEquals(RefreshResult.Updated(2), repo().refresh())
    }

    @Test fun `cached catalog is installed at startup`() = runTest {
        server.enqueue(MockResponse().setBody(catalog(2)))
        repo().refresh(force = true)
        PlatformDefinitions.reset()

        repo().loadCached()

        assertEquals(2, PlatformDefinitions.catalog.revision)
        assertEquals("vectrex", PlatformDefinitions.byFolderName["vectrex"]?.id)
    }

    @Test fun `catalog for a newer app is refused and the cache kept`() = runTest {
        server.enqueue(MockResponse().setBody(catalog(2)))
        server.enqueue(MockResponse().setBody(catalog(3, minApp = 99)))
        repo().refresh(force = true)

        assertEquals(RefreshResult.Failed("needs app versionCode 99, have 48"), repo().refresh(force = true))
        PlatformDefinitions.reset()
        repo().loadCached()
        assertEquals(2, PlatformDefinitions.catalog.revision)
    }
}
