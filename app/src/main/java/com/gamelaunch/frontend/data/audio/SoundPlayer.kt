package com.gamelaunch.frontend.data.audio

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
        vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** A friendly acknowledgement — e.g. "found your games". */
    fun found() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 160) }
        vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** The big finish. */
    fun celebrate() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 250) }
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40, 70, 110), -1))
    }

    private fun vibrate(effect: VibrationEffect) {
        runCatching { vibrator?.vibrate(effect) }
    }
}
