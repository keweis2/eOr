package com.gamelaunch.frontend.util

/** Version comparison shared by the app self-update and emulator-update checks. */
object VersionCompare {

    /**
     * Numeric, dot/dash-separated version compare (e.g. "1.5.0" > "1.4.0"); non-numeric parts are
     * ignored. Returns true when [latest] is strictly newer than [current].
     */
    fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split('.', '-').mapNotNull { it.toIntOrNull() }
        val c = current.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
