package com.muufin.compose.ui.util

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView


@Stable
class MuufinHaptics internal constructor(
    private val view: View,
) {
    fun tap() {
        
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun toggle() {
        
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm() {
        
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun longPress() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberMuufinHaptics(): MuufinHaptics {
    val view = LocalView.current
    return remember(view) { MuufinHaptics(view) }
}
