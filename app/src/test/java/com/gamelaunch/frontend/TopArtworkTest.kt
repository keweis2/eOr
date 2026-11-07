package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.ui.dualscreen.TopScreenImage
import com.gamelaunch.frontend.ui.dualscreen.resolveTopArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopArtworkTest {
    @Test fun `cover is used when no marquee screenshot or miximage exists`() {
        val media = GameMedia(gameId = 1, boxArtRemoteUrl = "https://example.com/minecraft.png")
        TopScreenImage.entries.forEach { type ->
            assertEquals(media.boxArtRemoteUrl, resolveTopArt(media, type)?.path)
        }
    }

    @Test fun `missing local artwork falls back to remote cover`() {
        val media = GameMedia(gameId = 1, screenshotLocalPath = "/missing/screenshot.png",
            boxArtLocalPath = "/missing/cover.png", boxArtRemoteUrl = "https://example.com/cover.png")
        assertEquals(media.boxArtRemoteUrl, resolveTopArt(media, TopScreenImage.SCREENSHOT)?.path)
    }

    @Test fun `preferred artwork takes precedence over cover`() {
        val media = GameMedia(gameId = 1, boxArtRemoteUrl = "https://example.com/cover.png",
            wheelLogoRemoteUrl = "https://example.com/logo.png", screenshotRemoteUrl = "https://example.com/shot.png")
        assertEquals(media.wheelLogoRemoteUrl, resolveTopArt(media, TopScreenImage.MARQUEE)?.path)
        assertEquals(media.screenshotRemoteUrl, resolveTopArt(media, TopScreenImage.SCREENSHOT)?.path)
    }

    @Test fun `game with no artwork uses title fallback`() {
        assertNull(resolveTopArt(GameMedia(gameId = 2), TopScreenImage.MARQUEE))
    }
    @Test fun `empty local file falls back to remote image`() {
        val file = java.io.File.createTempFile("empty-art", ".png")
        try {
            val media = GameMedia(gameId = 1, wheelLogoLocalPath = file.path,
                wheelLogoRemoteUrl = "https://example.com/logo.png")
            assertEquals(media.wheelLogoRemoteUrl, resolveTopArt(media, TopScreenImage.MARQUEE)?.path)
        } finally { file.delete() }
    }

    @Test fun `valid local file precedes remote image`() {
        val file = java.io.File.createTempFile("local-art", ".png")
        try {
            file.writeBytes(byteArrayOf(1))
            val media = GameMedia(gameId = 1, miximageLocalPath = file.path,
                miximageRemoteUrl = "https://example.com/mix.png")
            assertEquals(file.path, resolveTopArt(media, TopScreenImage.MIXIMAGE)?.path)
        } finally { file.delete() }
    }
}
