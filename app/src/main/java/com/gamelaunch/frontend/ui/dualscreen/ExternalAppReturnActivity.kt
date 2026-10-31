package com.gamelaunch.frontend.ui.dualscreen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.gamelaunch.frontend.data.playtime.PlaySessionTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Sits behind a top-screen external app. The menu can remain resumed on the other display throughout
 * use, so only this display's lifecycle can reliably detect the external app leaving it.
 */
@AndroidEntryPoint
class ExternalAppReturnActivity : ComponentActivity() {
    @Inject lateinit var externalAppSessionState: ExternalAppSessionState
    @Inject lateinit var playSessionTracker: PlaySessionTracker

    private var covered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        covered = savedInstanceState?.getBoolean("covered") ?: false
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        super.onResume()
        if (!covered) {
            val ready = pendingReady
            if (ready == null || !ready.complete(this)) finishAndRemoveTask()
            return
        }
        externalAppSessionState.end()
        lifecycleScope.launch {
            try {
                runCatching { playSessionTracker.onReturnedToEor() }
            } finally {
                finishAndRemoveTask()
            }
        }
    }

    override fun onPause() {
        covered = true
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("covered", covered)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private var pendingReady: CompletableDeferred<ExternalAppReturnActivity>? = null

        /** Wait until the return activity is resumed before allowing the external app to cover it. */
        suspend fun prepare(context: Context, options: Bundle): ExternalAppReturnActivity =
            withContext(Dispatchers.Main.immediate) {
                check(pendingReady == null) { "An external app launch is already in progress" }
                val ready = CompletableDeferred<ExternalAppReturnActivity>()
                pendingReady = ready
                try {
                    context.startActivity(
                        Intent(context, ExternalAppReturnActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                        options,
                    )
                    withTimeout(5_000) { ready.await() }
                } finally {
                    pendingReady = null
                }
            }
    }
}
