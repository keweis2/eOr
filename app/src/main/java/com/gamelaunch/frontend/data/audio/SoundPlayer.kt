package com.gamelaunch.frontend.data.audio

import android.os.Build
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight onboarding sound + haptics. Uses the system ToneGenerator (STREAM_MUSIC, so it's
 * silent when the device is muted) and the Vibrator — no bundled audio assets required. Swap in
 * res/raw SoundPool clips later if you want richer SFX.
 */
@Singleton
class SoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tone by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private val vibrator by lazy { context.getSystemService(Vibrator::class.java) }

    /** A small tick when advancing a step. */
    fun step() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60) }
        buzz(18)
    }

    /** A friendly acknowledgement — e.g. "found your games". */
    fun found() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 160) }
        buzz(25)
    }

    /** The big finish. */
    fun celebrate() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 250) }
        buzz(pattern = longArrayOf(0, 40, 60, 40, 70, 110))
    }

    /** A one-shot buzz of [ms], or a [pattern]. VibrationEffect is Android 8+; older uses the legacy calls. */
    @Suppress("DEPRECATION")
    private fun buzz(ms: Long = 0, pattern: LongArray? = null) {
        runCatching {
            val v = vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(
                    if (pattern != null) VibrationEffect.createWaveform(pattern, -1)
                    else VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else if (pattern != null) v.vibrate(pattern, -1) else v.vibrate(ms)
        }
    }
}
