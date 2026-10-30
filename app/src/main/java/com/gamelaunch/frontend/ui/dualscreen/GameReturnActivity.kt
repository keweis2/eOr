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
 * Sits behind a top-screen emulator. The menu can remain resumed on the other display throughout
 * gameplay, so only this display's lifecycle can reliably detect the emulator leaving it.
 */
@AndroidEntryPoint
class GameReturnActivity : ComponentActivity() {
    @Inject lateinit var gameSessionState: GameSessionState
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
        gameSessionState.end()
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
        private var pendingReady: CompletableDeferred<GameReturnActivity>? = null

        /** Wait until the return activity is resumed before allowing the emulator to cover it. */
        suspend fun prepare(context: Context, options: Bundle): GameReturnActivity =
            withContext(Dispatchers.Main.immediate) {
                check(pendingReady == null) { "A game launch is already in progress" }
                val ready = CompletableDeferred<GameReturnActivity>()
                pendingReady = ready
                try {
                    context.startActivity(
                        Intent(context, GameReturnActivity::class.java)
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
