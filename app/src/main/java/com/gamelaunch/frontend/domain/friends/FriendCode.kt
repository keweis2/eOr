package com.gamelaunch.frontend.domain.friends

/**
 * Encodes/decodes a friend's identity as a shareable `eor://friend/<deviceId>?n=<name>` deep link,
 * and validates raw Syncthing device ids. Pure string logic (no android.net.Uri) so it is unit-testable
 * and safe to reuse in the deep-link handler. Adding a friend from a parsed link must always be
 * user-confirmed — links can come from untrusted sources.
 */
object FriendCode {
    const val SCHEME = "eor"
    const val HOST = "friend"

    data class Parsed(val deviceId: String, val displayName: String?)

    /** Build a shareable link. Display name is optional context for the recipient's confirm dialog. */
    fun buildLink(deviceId: String, displayName: String? = null): String {
        val base = "$SCHEME://$HOST/${deviceId.trim().uppercase()}"
        return if (displayName.isNullOrBlank()) base else "$base?n=${encodeName(displayName.trim())}"
    }

    /**
     * Accepts either a full `eor://friend/<id>?n=<name>` link or a bare device id (e.g. pasted).
     * Returns null if no valid device id is present.
     */
    fun parse(input: String): Parsed? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("$SCHEME://", ignoreCase = true)) parseLink(trimmed)
        else normalizeDeviceId(trimmed)?.let { Parsed(it, null) }
    }

    fun isValidDeviceId(candidate: String): Boolean = normalizeDeviceId(candidate) != null

    private fun parseLink(link: String): Parsed? {
        // eor://friend/<id>[?n=<name>]
        val afterScheme = link.substringAfter("://")
        val host = afterScheme.substringBefore('/', "")
        if (!host.equals(HOST, ignoreCase = true)) return null
        val pathAndQuery = afterScheme.substringAfter('/', "")
        val rawId = pathAndQuery.substringBefore('?')
        val deviceId = normalizeDeviceId(rawId) ?: return null
        val name = if ('?' in pathAndQuery) {
            pathAndQuery.substringAfter('?').split('&')
                .firstOrNull { it.startsWith("n=") }
                ?.substringAfter("n=")
                ?.let { decodeName(it) }
                ?.takeIf { it.isNotBlank() }
        } else null
        return Parsed(deviceId, name)
    }

    /**
     * A Syncthing device id is base32 (A–Z, 2–7) grouped by dashes with luhn check chars — 56 payload
     * chars (63 with dashes). We normalize by upper-casing and validate the character set and length
     * without re-deriving check digits (Syncthing does the strict check when the id is added).
     */
    private fun normalizeDeviceId(raw: String): String? {
        val cleaned = raw.trim().uppercase()
        if (cleaned.isEmpty()) return null
        val stripped = cleaned.replace("-", "")
        if (stripped.length !in 52..56) return null
        if (!stripped.all { it in 'A'..'Z' || it in '2'..'7' }) return null
        return cleaned
    }

    // Minimal, dependency-free percent-encoding for the display-name query value.
    private fun encodeName(name: String): String = buildString {
        for (b in name.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c.toChar().isLetterOrDigit() || c.toChar() in "-_.~") append(c.toChar())
            else append('%').append("%02X".format(c))
        }
    }

    private fun decodeName(value: String): String {
        val bytes = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            when {
                ch == '%' && i + 2 < value.length -> {
                    bytes.add(value.substring(i + 1, i + 3).toInt(16).toByte()); i += 3
                }
                ch == '+' -> { bytes.add(' '.code.toByte()); i++ }
                else -> { bytes.add(ch.code.toByte()); i++ }
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
