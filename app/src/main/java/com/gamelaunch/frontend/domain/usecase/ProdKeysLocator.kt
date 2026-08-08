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
    /** Every usable key file found on the storage volumes. The caller validates it against the
     * package being opened, so a stale emulator key file cannot mask a current one. */
    fun findAll(): Sequence<File> = findAllInRoots(
        StorageUtils.getStorageVolumes(context).map { (_, rootPath) -> File(rootPath) }
    )

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
