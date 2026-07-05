package com.kangle.kardleaf.data.utils

/**
 * Centralized log tags for new diagnostics.
 * Keep new log points on these tags instead of creating scattered tag strings.
 */
object KardLeafLogTags {
    const val STARTUP_PERF = "KardLeafStartupPerf"
    const val USER_PERF = "KardLeafUserPerf"
    const val DASHBOARD_SCROLL = "KardLeafDashboardScroll"
    const val GESTURE_TRACE = "KardLeafGestureTrace"
}

object KardLeafPerfLog {
    fun msPerPx(elapsedMs: Long, deltaPx: Int): String =
        if (deltaPx > 0) {
            String.format(java.util.Locale.US, "%.3f", elapsedMs.toFloat() / deltaPx)
        } else {
            "0.000"
        }

    fun avgFrame(elapsedMs: Long, frameCount: Int): String =
        if (frameCount > 0) {
            String.format(java.util.Locale.US, "%.1f", elapsedMs.toFloat() / frameCount)
        } else {
            "0.0"
        }

    fun noteSizeTier(length: Int): String = when {
        length < 10_000 -> "lt_1w"
        length < 50_000 -> "1w_5w"
        length < 100_000 -> "5w_10w"
        length < 1_000_000 -> "10w_100w"
        else -> "gte_100w"
    }
}
