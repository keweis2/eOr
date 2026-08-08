package com.gamelaunch.frontend.domain.usecase

import android.content.Context
import com.gamelaunch.frontend.util.StorageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds a Switch key file which the user has already placed on local storage.
 *
 * eOr deliberately never imports, copies, synchronises, or reads the contents of the file here.
 * Example use: a NSP artwork decoder can open the returned file only while it is doing local decoding.
 */
@Singleton
class ProdKeysLocator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheLock = Any()
    @Volatile private var cache: List<File>? = null

    /**
     * Every usable key file found on the storage volumes. The caller validates it against the
     * package being opened, so a stale emulator key file cannot mask a current one.
     *
     * The completed search is materialised and memoised: it walks the entire internal-storage and
     * SD-card trees, and a library scan asks for keys once per NSP. Without the cache that full
     * walk would run once for every NSP on every scan — including the common no-keys case, where
     * the walk still has to complete to prove the result is empty. Call [invalidate] at the start
     * of a scan so keys the user added since the last scan are picked up.
     */
    fun findAll(): Sequence<File> {
        cache?.let { return it.asSequence() }
        return synchronized(cacheLock) {
            cache ?: findAllInRoots(
                StorageUtils.getStorageVolumes(context).map { (_, rootPath) -> File(rootPath) }
            ).toList().also { cache = it }
        }.asSequence()
    }

    /** Drops the memoised result so the next [findAll] re-walks storage. Called at scan start. */
    fun invalidate() {
        cache = null
    }

    internal companion object {
        /** Searches every readable directory below the supplied storage roots, without assuming
         * an emulator name or a particular folder layout. */
        fun findAllInRoots(roots: List<File>): Sequence<File> = roots
            .asSequence()
            .filter { it.isDirectory && it.canRead() }
            .flatMap { root ->
                root.walkTopDown()
                    .onEnter { it.canRead() }
                    .asSequence()
            }
            .filter { file ->
                file.isFile &&
                    file.name.equals("prod.keys", ignoreCase = true) &&
                    file.canRead() &&
                    file.length() > 0L
            }
    }
}
