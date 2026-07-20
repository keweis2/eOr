package com.gamelaunch.frontend.domain.friends

import kotlin.random.Random

/**
 * Generates a friendly, retro-flavoured display name for users who don't set their own
 * (e.g. "PixelFox-4821"). Purely local — no uniqueness guarantee is needed because the
 * Syncthing device id disambiguates friends; the handle is only a human-readable label.
 */
object RandomHandle {

    private val adjectives = listOf(
        "Pixel", "Turbo", "Neon", "Retro", "Cosmic", "Arcade", "Blast", "Chrono", "Hyper", "Mega",
        "Ultra", "Glitch", "Quantum", "Laser", "Astro", "Vapor", "Digital", "Rogue", "Shadow", "Golden",
    )

    private val nouns = listOf(
        "Fox", "Raccoon", "Comet", "Ranger", "Wizard", "Ninja", "Falcon", "Goomba", "Dragon", "Knight",
        "Otter", "Phoenix", "Samurai", "Racer", "Gamer", "Yeti", "Panda", "Robot", "Viper", "Wolf",
    )

    /** e.g. "NeonFalcon-3172". [random] is injectable for deterministic tests. */
    fun generate(random: Random = Random.Default): String {
        val adj = adjectives[random.nextInt(adjectives.size)]
        val noun = nouns[random.nextInt(nouns.size)]
        val number = random.nextInt(1000, 10000) // always 4 digits
        return "$adj$noun-$number"
    }
}
