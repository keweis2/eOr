package com.gamelaunch.frontend.domain.usecase

import android.content.Context
import android.os.Environment
import android.util.Xml
import com.gamelaunch.frontend.util.StorageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import javax.inject.Inject

/**
 * Finds an existing ES-DE install so onboarding can offer to reuse it ("I found your ES-DE
 * library") instead of downloading the same artwork again. ES-DE keeps its data in a top-level
 * folder on internal storage or an SD card (`ES-DE` by default); its settings file names the ROM
 * folder and, optionally, a custom media folder.
 */
class DetectEsdeUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class EsdeInstall(
        /** The ES-DE data folder (holds `downloaded_media`, `gamelists`, `settings`). */
        val rootPath: String,
        /** The folder holding per-system media — pass this as eOr's media folder. */
        val mediaPath: String?,
        /** ES-DE's configured ROM folder, when set to an absolute, existing path. */
        val romDirectory: String?,
        /** How many systems have downloaded media (a quick "is there anything?" signal). */
        val mediaSystemCount: Int
    )

    suspend operator fun invoke(): EsdeInstall? = withContext(Dispatchers.IO) {
        val roots = buildList {
            add(Environment.getExternalStorageDirectory().absolutePath)
            StorageUtils.getStorageVolumes(context).forEach { add(it.second) }
        }.distinct()

        // ES-DE lets users put its data folder anywhere and name it freely ("ES-DE", "ESDE", …), so
        // look at every top-level folder for its signature settings file — one listing per volume.
        roots.asSequence()
            .flatMap { root -> File(root).listFiles()?.asSequence() ?: emptySequence() }
            .filter { it.isDirectory && File(it, "settings/es_settings.xml").isFile }
            .distinctBy { it.canonicalPath }
            .mapNotNull(::inspect)
            .maxByOrNull { it.mediaSystemCount }
    }

    private fun inspect(root: File): EsdeInstall? {
        val settings = readSettings(File(root, "settings/es_settings.xml"))
        val romDir = settings["ROMDirectory"]?.takeIf { it.startsWith("/") && File(it).isDirectory }
        val customMedia = settings["MediaDirectory"]?.takeIf { it.startsWith("/") && File(it).isDirectory }
        val media = customMedia?.let(::File) ?: File(root, "downloaded_media").takeIf { it.isDirectory }
        val systems = media?.listFiles()?.count { dir -> dir.isDirectory && dir.list()?.isNotEmpty() == true } ?: 0
        if (media == null && romDir == null) return null
        return EsdeInstall(root.absolutePath, media?.absolutePath, romDir, systems)
    }

    /** `<string name="X" value="Y" />` entries from es_settings.xml. Empty if missing/unreadable. */
    private fun readSettings(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val out = HashMap<String, String>()
        runCatching {
            file.inputStream().use { stream ->
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(stream, null)
                }
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "string") {
                        val name = parser.getAttributeValue(null, "name")
                        val value = parser.getAttributeValue(null, "value")
                        if (name != null && !value.isNullOrBlank()) out[name] = value
                    }
                    event = parser.next()
                }
            }
        }
        return out
    }
}
