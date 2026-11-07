package com.gamelaunch.frontend

import android.app.Application
import androidx.activity.ComponentActivity
import com.gamelaunch.frontend.ui.dualscreen.ArtworkActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class ArtworkActivityOwnershipTest {
    @Test fun `outgoing menu cannot invalidate replacement artwork request`() {
        val oldMenu = mock<ComponentActivity>()
        val newMenu = mock<ComponentActivity>()
        ArtworkActivity.attachMenu(oldMenu)
        ArtworkActivity.attachMenu(newMenu)
        val request = ArtworkActivity.nextRequest()
        try {
            ArtworkActivity.dismiss(oldMenu)
            assertEquals(request + 1, ArtworkActivity.nextRequest())
        } finally {
            ArtworkActivity.detachMenu(newMenu)
        }
    }

    @Test fun `owning menu invalidates its pending artwork request when stopped`() {
        val menu = mock<ComponentActivity>()
        ArtworkActivity.attachMenu(menu)
        val request = ArtworkActivity.nextRequest()
        try {
            ArtworkActivity.dismiss(menu)
            assertEquals(request + 2, ArtworkActivity.nextRequest())
        } finally {
            ArtworkActivity.detachMenu(menu)
        }
    }
}
