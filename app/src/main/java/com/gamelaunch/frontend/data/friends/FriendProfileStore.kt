package com.gamelaunch.frontend.data.friends

import android.content.Context
import com.gamelaunch.frontend.domain.friends.FriendProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the on-disk layout of the Friends P2P folders under filesDir/eor-friends:
 *   out/profile.json         — my send-only profile (synced TO friends)
 *   in/<friendDeviceId>/profile.json — each friend's receive-only profile (synced FROM them)
 * These directories are what SyncthingController registers as sendonly/receiveonly folders.
 */
@Singleton
class FriendProfileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val root: File get() = File(context.filesDir, "eor-friends").apply { mkdirs() }

    val outDir: File get() = File(root, "out").apply { mkdirs() }
    private val outFile: File get() = File(outDir, PROFILE_FILE)

    fun inDir(friendDeviceId: String): File =
        File(File(root, "in"), friendDeviceId.trim().uppercase()).apply { mkdirs() }

    suspend fun writeMyProfile(profile: FriendProfile) = withContext(Dispatchers.IO) {
        outFile.writeText(profile.encode())
    }

    /** Empty my outbound profile so nothing is shared while the feature is off. */
    suspend fun clearMyProfile() = withContext(Dispatchers.IO) {
        if (outFile.exists()) outFile.delete()
    }

    suspend fun readFriendProfile(friendDeviceId: String): FriendProfile? = withContext(Dispatchers.IO) {
        val f = File(inDir(friendDeviceId), PROFILE_FILE)
        if (!f.exists()) null else runCatching { FriendProfile.decode(f.readText()) }.getOrNull()
    }

    companion object {
        const val PROFILE_FILE = "profile.json"
    }
}
