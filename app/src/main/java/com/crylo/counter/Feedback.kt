package com.crylo.counter

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

/** Haptics and the optional target sound. */
class Feedback(context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    /** A short, crisp click for every count. */
    fun tap() = vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))

    /**
     * A long-short-long pattern that can't be mistaken for a normal tap, so the target is
     * noticed with the phone in a pocket or the eyes closed.
     */
    fun target(withSound: Boolean) {
        vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 220, 90, 110, 90, 320),
                intArrayOf(0, 255, 0, 200, 0, 255),
                -1,
            ),
        )
        if (withSound) beep()
    }

    private fun vibrate(effect: VibrationEffect) {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Media usage follows the "media vibration" setting rather than the ringer, so
            // counting in a silent room (mosque, temple, class) still buzzes.
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
        } else {
            vibrator.vibrate(effect)
        }
    }

    private fun beep() {
        // The notification stream respects the ringer: silent mode means no sound.
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            Thread {
                Thread.sleep(600)
                tone.release()
            }.start()
        }
    }
}
