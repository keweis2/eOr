package com.gamelaunch.frontend.ui.dualscreen

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.gamelaunch.frontend.MainActivity
import com.gamelaunch.frontend.data.playtime.PlaySessionTracker
import com.gamelaunch.frontend.platform.display.DualScreenManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import android.app.ActivityOptions

/**
 * Sits behind an external app on its launch display. A top-screen return restores artwork;
 * a bottom-screen return explicitly raises the menu rather than relying on Android's task order.
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
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_MENU, false)) {
            // Reused app tasks can otherwise reveal the device launcher when Back is pressed.
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ActivityOptions.makeBasic().setLaunchDisplayId(
                    DualScreenManager.currentDisplayId(this),
                ).toBundle()
            } else null
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                ),
                options,
            )
            finishAndRemoveTask()
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
        private const val EXTRA_RETURN_TO_MENU = "return_to_menu"
        private var pendingReady: CompletableDeferred<ExternalAppReturnActivity>? = null

        /** Wait until the return activity is resumed before allowing the external app to cover it. */
        suspend fun prepare(
            context: Context,
            options: Bundle,
            returnToMenu: Boolean = false,
        ): ExternalAppReturnActivity =
            withContext(Dispatchers.Main.immediate) {
                check(pendingReady == null) { "An external app launch is already in progress" }
                val ready = CompletableDeferred<ExternalAppReturnActivity>()
                pendingReady = ready
                try {
                    context.startActivity(
                        Intent(context, ExternalAppReturnActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            .putExtra(EXTRA_RETURN_TO_MENU, returnToMenu),
                        options,
                    )
                    withTimeout(5_000) { ready.await() }
                } finally {
                    pendingReady = null
                }
            }
    }
}

/** Injectable boundary for preparing the activity behind an external app. */
class ExternalAppReturnHost @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun prepare(options: Bundle, returnToMenu: Boolean): ExternalAppReturnActivity =
        ExternalAppReturnActivity.prepare(context, options, returnToMenu)
}
