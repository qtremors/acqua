package dev.qtremors.acqua.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class HapticSignal { CLICK, COMPLETE, WARNING }

fun Context.performHaptic(signal: HapticSignal) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val effect = when (signal) {
            HapticSignal.CLICK -> VibrationEffect.EFFECT_CLICK
            HapticSignal.COMPLETE -> VibrationEffect.EFFECT_DOUBLE_CLICK
            HapticSignal.WARNING -> VibrationEffect.EFFECT_HEAVY_CLICK
        }
        vibrator.vibrate(VibrationEffect.createPredefined(effect))
    } else {
        @Suppress("DEPRECATION")
        when (signal) {
            HapticSignal.CLICK -> vibrator.vibrate(8)
            HapticSignal.COMPLETE -> vibrator.vibrate(longArrayOf(0, 12, 50, 15), -1)
            HapticSignal.WARNING -> vibrator.vibrate(30)
        }
    }
}
