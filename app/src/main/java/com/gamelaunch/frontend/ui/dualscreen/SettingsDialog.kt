package com.gamelaunch.frontend.ui.dualscreen

import android.app.Presentation
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.view.WindowCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** A dialog on a Presentation must itself be a Presentation, with the same display/window type. */
@Composable
fun SettingsDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val panels = LocalSettingsPanels.current
    val dismiss by rememberUpdatedState(onDismissRequest)
    val dismissOnBack by rememberUpdatedState(properties.dismissOnBackPress)
    val displayContext = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<SettingsDisplayContext>().firstOrNull()
    if (displayContext == null) {
        androidx.compose.ui.window.Dialog(onDismissRequest, properties) {
            val view = LocalView.current
            DisposableEffect(panels, view) {
                panels?.addModal(view, dismissOnBack = { dismissOnBack }) { dismiss() }
                onDispose { panels?.removeModal(view) }
            }
            content()
        }
        return
    }
    val activity = generateSequence<Context>(displayContext) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<ComponentActivity>().first()
    val parent = rememberCompositionContext()
    val latestContent by rememberUpdatedState(content)
    DisposableEffect(displayContext, parent, properties, panels) {
        lateinit var dialogView: ComposeView
        val dialog = object : Presentation(activity, displayContext.controlsDisplay) {
            @Deprecated("Deprecated in Android")
            override fun onBackPressed() {
                if (panels?.back() != true && dismissOnBack) cancel()
            }
        }.apply {
            setCancelable(true)
            setCanceledOnTouchOutside(properties.dismissOnClickOutside)
            setOnCancelListener { dismiss() }
            val view = ComposeView(displayContext).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setParentCompositionContext(parent)
                setContent { latestContent() }
            }
            dialogView = view
            panels?.addModal(view, dismissOnBack = { dismissOnBack }) { dismiss() }
            setContentView(view)
            window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, properties.decorFitsSystemWindows)
                val secure = properties.securePolicy == SecureFlagPolicy.SecureOn ||
                    (properties.securePolicy == SecureFlagPolicy.Inherit &&
                        activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                window.setBackgroundDrawableResource(android.R.color.transparent)
            }
            show()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            val width = if (properties.usePlatformDefaultWidth) {
                val metrics = displayContext.resources.displayMetrics
                minOf((560 * metrics.density).toInt(), metrics.widthPixels - (48 * metrics.density).toInt())
            } else WindowManager.LayoutParams.MATCH_PARENT
            window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        onDispose {
            panels?.removeModal(dialogView)
            dialog.dismiss()
            dialogView.disposeComposition()
        }
    }
}

@Composable
fun SettingsAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val presentation = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .any { it is SettingsDisplayContext }
    if (!presentation) {
        val panels = LocalSettingsPanels.current
        val dismiss by rememberUpdatedState(onDismissRequest)
        AlertDialog(onDismissRequest, confirmButton, modifier, dismissButton = dismissButton,
            title = title, text = {
                val view = LocalView.current
                DisposableEffect(panels, view) {
                    panels?.addModal(view) { dismiss() }
                    onDispose { panels?.removeModal(view) }
                }
                text?.invoke()
            })
        return
    }
    SettingsDialog(onDismissRequest) {
        Surface(modifier, shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                title?.let { ProvideTextStyle(MaterialTheme.typography.headlineSmall, it) }
                text?.let { ProvideTextStyle(MaterialTheme.typography.bodyMedium, it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    dismissButton?.invoke()
                    Spacer(Modifier.width(8.dp))
                    confirmButton()
                }
            }
        }
    }
}
