package com.gamelaunch.frontend.ui.component

import androidx.annotation.DrawableRes
import com.gamelaunch.frontend.R
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions

private val platformLabels = mapOf(
    "nes"      to "NES",    "snes"    to "SNES",    "n64"    to "N64",
    "gb"       to "GB",     "gbc"     to "GBC",     "gba"    to "GBA",
    "nds"      to "NDS",    "3ds"     to "3DS",     "switch" to "Switch",
    "ps1"      to "PS1",    "ps2"     to "PS2",     "ps3"    to "PS3",
    "psp"      to "PSP",    "dc"      to "DC",      "saturn" to "Saturn",
    "genesis"  to "GEN",    "gg"      to "GG",      "sms"    to "SMS",
    "pce"      to "PCE",    "neogeo"  to "Neo·Geo", "arcade" to "Arcade",
    "msx"      to "MSX",    "lynx"    to "Lynx",    "atari"  to "Atari"
)

/** Short pill label for a platform (falls back to the upper-cased id). */
fun platformLabel(platformId: String): String =
    platformLabels[platformId] ?: platformId.uppercase()

/** Full display name for a platform (falls back to the pill label). */
fun platformDisplayName(platformId: String): String =
    PlatformDefinitions.byId[platformId]?.displayName ?: platformLabel(platformId)

/**
 * Full-colour console illustrations from KyleBing's retro-game-console-icons (GPL-3.0). The whole
 * pack is bundled, keyed by the pack's own console abbreviations, so adding a new platform usually
 * needs no new asset — only (optionally) an alias below if our platformId differs from the pack key.
 */
private val iconByKey: Map<String, Int> = mapOf(
        "32x" to R.drawable.ic_sys_32x,
        "5200" to R.drawable.ic_sys_5200,
        "7800" to R.drawable.ic_sys_7800,
        "amiga" to R.drawable.ic_sys_amiga,
        "arcade" to R.drawable.ic_sys_arcade,
        "arduboy" to R.drawable.ic_sys_arduboy,
        "atari" to R.drawable.ic_sys_atari,
        "atari800" to R.drawable.ic_sys_atari800,
        "atarist" to R.drawable.ic_sys_atarist,
        "atomiswave" to R.drawable.ic_sys_atomiswave,
        "c64" to R.drawable.ic_sys_c64,
        "chai" to R.drawable.ic_sys_chai,
        "col" to R.drawable.ic_sys_col,
        "cpc" to R.drawable.ic_sys_cpc,
        "cps1" to R.drawable.ic_sys_cps1,
        "cps2" to R.drawable.ic_sys_cps2,
        "cps3" to R.drawable.ic_sys_cps3,
        "dc" to R.drawable.ic_sys_dc,
        "doom" to R.drawable.ic_sys_doom,
        "dos" to R.drawable.ic_sys_dos,
        "easyrpg" to R.drawable.ic_sys_easyrpg,
        "fairchild" to R.drawable.ic_sys_fairchild,
        "fc" to R.drawable.ic_sys_fc,
        "fds" to R.drawable.ic_sys_fds,
        "ffplay" to R.drawable.ic_sys_ffplay,
        "gb" to R.drawable.ic_sys_gb,
        "gba" to R.drawable.ic_sys_gba,
        "gbc" to R.drawable.ic_sys_gbc,
        "gg" to R.drawable.ic_sys_gg,
        "gw" to R.drawable.ic_sys_gw,
        "itv" to R.drawable.ic_sys_itv,
        "lynx" to R.drawable.ic_sys_lynx,
        "mame" to R.drawable.ic_sys_mame,
        "md" to R.drawable.ic_sys_md,
        "megaduck" to R.drawable.ic_sys_megaduck,
        "ms" to R.drawable.ic_sys_ms,
        "msu1" to R.drawable.ic_sys_msu1,
        "msumd" to R.drawable.ic_sys_msumd,
        "msx" to R.drawable.ic_sys_msx,
        "n64" to R.drawable.ic_sys_n64,
        "n64dd" to R.drawable.ic_sys_n64dd,
        "naomi" to R.drawable.ic_sys_naomi,
        "nds" to R.drawable.ic_sys_nds,
        "neocd" to R.drawable.ic_sys_neocd,
        "neogeo" to R.drawable.ic_sys_neogeo,
        "ngc" to R.drawable.ic_sys_ngc,
        "ngp" to R.drawable.ic_sys_ngp,
        "ngpc" to R.drawable.ic_sys_ngpc,
        "ody" to R.drawable.ic_sys_ody,
        "openbor" to R.drawable.ic_sys_openbor,
        "pc88" to R.drawable.ic_sys_pc88,
        "pc98" to R.drawable.ic_sys_pc98,
        "pce" to R.drawable.ic_sys_pce,
        "pcecd" to R.drawable.ic_sys_pcecd,
        "pcfx" to R.drawable.ic_sys_pcfx,
        "pico" to R.drawable.ic_sys_pico,
        "poke" to R.drawable.ic_sys_poke,
        "ports" to R.drawable.ic_sys_ports,
        "ps" to R.drawable.ic_sys_ps,
        "psp" to R.drawable.ic_sys_psp,
        "quake" to R.drawable.ic_sys_quake,
        "satella" to R.drawable.ic_sys_satella,
        "saturn" to R.drawable.ic_sys_saturn,
        "scummvm" to R.drawable.ic_sys_scummvm,
        "segacd" to R.drawable.ic_sys_segacd,
        "segasgone" to R.drawable.ic_sys_segasgone,
        "sfc" to R.drawable.ic_sys_sfc,
        "sgb" to R.drawable.ic_sys_sgb,
        "sgfx" to R.drawable.ic_sys_sgfx,
        "sufami" to R.drawable.ic_sys_sufami,
        "supervision" to R.drawable.ic_sys_supervision,
        "tic" to R.drawable.ic_sys_tic,
        "vb" to R.drawable.ic_sys_vb,
        "vdp" to R.drawable.ic_sys_vdp,
        "vectrex" to R.drawable.ic_sys_vectrex,
        "wolf" to R.drawable.ic_sys_wolf,
        "ws" to R.drawable.ic_sys_ws,
        "wsc" to R.drawable.ic_sys_wsc,
        "x68000" to R.drawable.ic_sys_x68000,
        "zxs" to R.drawable.ic_sys_zxs,
        // Modern consoles the KyleBing pack doesn't cover — custom flat-style icons.
        "switch" to R.drawable.ic_sys_switch,
        "3ds" to R.drawable.ic_sys_3ds,
        "ps2" to R.drawable.ic_sys_ps2,
        "ps3" to R.drawable.ic_sys_ps3,
        "psvita" to R.drawable.ic_sys_psvita,
        "wii" to R.drawable.ic_sys_wii,
        "wiiu" to R.drawable.ic_sys_wiiu,
        "gamecube" to R.drawable.ic_sys_gamecube,
        "3do" to R.drawable.ic_sys_3do,
)

// our platformId -> pack key, only where the two differ (direct id matches resolve automatically)
private val platformIconAlias: Map<String, String> = mapOf(
    "nes" to "fc", "famicom" to "fc",
    "snes" to "sfc",
    "genesis" to "md", "megadrive" to "md",
    "sms" to "ms", "mastersystem" to "ms",
    "gamegear" to "gg",
    "ps1" to "ps", "psx" to "ps",
    "atari2600" to "atari",
    "gc" to "gamecube",
    "vita" to "psvita",
    "fbneo" to "arcade",
    "neogeocd" to "neocd",
    "pcengine" to "pce", "tg16" to "pce",
    "pcenginecd" to "pcecd",
    "wonderswan" to "ws", "wonderswancolor" to "wsc",
    "virtualboy" to "vb",
    "colecovision" to "col",
    "intellivision" to "itv",
)

/** Console illustration for a platform, or null if the pack has none (callers fall back to [platformPadIcon]). */
@DrawableRes
fun platformIcon(platformId: String): Int? =
    iconByKey[platformIconAlias[platformId] ?: platformId]

/** A controller silhouette that fits each console family — a bit of whimsy. */
@DrawableRes
fun platformPadIcon(platformId: String): Int = when (platformId) {
    "nes", "famicom", "fds" -> R.drawable.ic_pad_nes
    "gb", "gbc", "gba", "nds", "3ds", "psp", "gg", "lynx", "ngp", "ws" ->
        R.drawable.ic_pad_handheld
    "arcade", "mame", "neogeo", "cps", "cps1", "cps2", "cps3", "fbneo" ->
        R.drawable.ic_pad_arcade
    else -> R.drawable.ic_pad_gamepad   // snes, n64, genesis, sms, ps*, dc, saturn, switch, …
}
