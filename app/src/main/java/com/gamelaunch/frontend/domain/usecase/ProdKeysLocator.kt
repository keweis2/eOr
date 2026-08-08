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
    @Volatile private var cacheComputedAt = 0L

    /**
     * Every usable key file found on the storage volumes. The caller validates it against the
     * package being opened, so a stale emulator key file cannot mask a current one.
     *
     * The result is materialised and memoised for [CACHE_TTL_MS]: the underlying search walks the
     * entire internal-storage and SD-card trees, and a library scan asks for keys once per NSP.
     * Without the cache that full walk would run once for every NSP on every scan — including the
     * common no-keys case, where the walk still has to complete to prove the result is empty.
     */
    fun findAll(): Sequence<File> {
        cache?.let { if (System.currentTimeMillis() - cacheComputedAt < CACHE_TTL_MS) return it.asSequence() }
        return synchronized(cacheLock) {
            val existing = cache
            if (existing != null && System.currentTimeMillis() - cacheComputedAt < CACHE_TTL_MS) {
                existing
            } else {
                findAllInRoots(
                    StorageUtils.getStorageVolumes(context).map { (_, rootPath) -> File(rootPath) }
                ).toList().also {
                    cache = it
                    cacheComputedAt = System.currentTimeMillis()
                }
            }
        }.asSequence()
    }

    internal companion object {
        /** How long a completed key search is reused. Long enough that a single library scan walks
         * storage once instead of once per NSP; short enough that keys added between scans are
         * picked up on the next scan. */
        const val CACHE_TTL_MS = 60_000L

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
