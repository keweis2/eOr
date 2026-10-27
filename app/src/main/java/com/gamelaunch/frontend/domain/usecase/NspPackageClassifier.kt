package com.gamelaunch.frontend.domain.usecase

import java.io.File

internal object NspPackageClassifier {
    /**
     * Switch update and DLC NSPs cannot be launched as standalone games, so don't add
     * them to the library alongside their base NSP. Updates are commonly named with a
     * non-zero version tag (for example "[v65536]") or a patch title ID ending in 800;
     * DLC packages conventionally include the standalone word "DLC" or a bracketed
     * Add-On label.
     *
     * This intentionally only recognises explicit, conventional markers. An NSP with
     * an unfamiliar filename remains visible rather than risking a false positive.
     */
    fun isSupplemental(file: File): Boolean {
        if (!file.extension.equals("nsp", ignoreCase = true)) {
            return false
        }

        val filename = file.nameWithoutExtension
        return NON_ZERO_VERSION_TAG.containsMatchIn(filename) ||
            DLC_TAG.containsMatchIn(filename) ||
            TITLE_ID_TAG.findAll(filename).any { match ->
                match.groupValues[1].endsWith("800", ignoreCase = true)
            }
    }

    private val NON_ZERO_VERSION_TAG = Regex("""\[\s*v[1-9]\d*\s*]""", RegexOption.IGNORE_CASE)
    private val DLC_TAG = Regex(
        """(?:\bDLC\b|\[\s*add[ -]?on\s*]|\(\s*add[ -]?on\s*\))""",
        RegexOption.IGNORE_CASE
    )
    private val TITLE_ID_TAG = Regex("""\[([0-9a-f]{16})]""", RegexOption.IGNORE_CASE)
}
