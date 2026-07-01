package moe.antimony.hoshi.features.jiten

import android.os.SystemClock

internal object JitenDevMode {
    @Volatile
    var enabled: Boolean = false
        private set

    private val toggles = ArrayDeque<Long>()

    @Synchronized
    fun recordEnableToggle(now: Long = SystemClock.elapsedRealtime()): Boolean {
        if (enabled) return false
        toggles.addLast(now)
        while (toggles.isNotEmpty() && now - toggles.first() > ActivationWindowMillis) {
            toggles.removeFirst()
        }
        if (toggles.size < ActivationToggleCount) return false
        enabled = true
        toggles.clear()
        return true
    }

    private const val ActivationToggleCount = 5
    private const val ActivationWindowMillis = 5_000L
}
