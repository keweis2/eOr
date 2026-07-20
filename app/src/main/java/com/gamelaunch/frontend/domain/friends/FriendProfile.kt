package com.gamelaunch.frontend.domain.friends

import org.json.JSONObject

/** The last game a user launched, as shared with friends. Uses portable identifiers, no local paths. */
data class LastPlayed(
    val title: String,
    val platform: String,
    val md5: String?,
    val at: Long
)

/** A user's public RetroAchievements standing, as shared with friends. */
data class RaInfo(
    val username: String,
    val points: Int,
    val softcorePoints: Int
)

/**
 * The small public snapshot each device writes to its send-only profile folder as `profile.json`
 * and syncs to friends. Contains only user-authored / already-public data — never credentials,
 * tokens, save files, or filesystem paths.
 */
data class FriendProfile(
    val deviceId: String,
    val displayName: String,
    val lastPlayed: LastPlayed?,
    val ra: RaInfo?,
    val updatedAt: Long
) {
    fun encode(): String = JSONObject().apply {
        put("v", VERSION)
        put("deviceId", deviceId)
        put("displayName", displayName)
        lastPlayed?.let { lp ->
            put("lastPlayed", JSONObject().apply {
                put("title", lp.title)
                put("platform", lp.platform)
                lp.md5?.let { put("md5", it) }
                put("at", lp.at)
            })
        }
        ra?.let { r ->
            put("ra", JSONObject().apply {
                put("username", r.username)
                put("points", r.points)
                put("softcorePoints", r.softcorePoints)
            })
        }
        put("updatedAt", updatedAt)
    }.toString()

    companion object {
        const val VERSION = 1

        /** Tolerant decode: returns null on malformed input or a missing required field. */
        fun decode(json: String): FriendProfile? = runCatching {
            val o = JSONObject(json)
            val deviceId = o.optString("deviceId").takeIf { it.isNotBlank() } ?: return null
            val displayName = o.optString("displayName").takeIf { it.isNotBlank() } ?: return null
            val lastPlayed = o.optJSONObject("lastPlayed")?.let { lp ->
                val title = lp.optString("title").takeIf { it.isNotBlank() } ?: return@let null
                LastPlayed(
                    title = title,
                    platform = lp.optString("platform"),
                    md5 = lp.optString("md5").takeIf { it.isNotBlank() },
                    at = lp.optLong("at")
                )
            }
            val ra = o.optJSONObject("ra")?.let { r ->
                val username = r.optString("username").takeIf { it.isNotBlank() } ?: return@let null
                RaInfo(username, r.optInt("points"), r.optInt("softcorePoints"))
            }
            FriendProfile(deviceId, displayName, lastPlayed, ra, o.optLong("updatedAt"))
        }.getOrNull()
    }
}
