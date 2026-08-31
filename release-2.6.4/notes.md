## eOr 2.6.4  <!-- DRAFT — review before publishing -->

### An apology, and the real fix for image loading

First, I'm sorry. Box art loading has been slow and flaky for a few versions now, and past attempts to fix it didn't get to the bottom of it. This is an emergency mid-week release because I finally found the actual cause — and fixed it properly.

**What was really wrong:** your box art is stored as large (0.5–1.3 MB) image files on your SD card, and the app was re-reading and decoding a full-size file *every single time* a cover scrolled into view — just to show it at thumbnail size. That's why systems took forever to fill in, and why covers you'd already seen would reload when you scrolled back. It was never your device being slow.

**The fix:** eOr now creates a tiny thumbnail of each cover the first time it sees it, and loads that afterward — around 50× less work. It also builds those thumbnails for your whole library quietly in the background after launch, so browsing is fast everywhere, not just where you've already been. Once you've seen a cover, it stays put and won't reload.

On my Retroid Pocket 4 Pro, screens that used to take ~9 seconds to fill now fill almost instantly, and scrolling to the bottom of a system and back no longer reloads a thing.

### Notes
- The first launch after updating does a one-time background pass to thumbnail your library. You can use the app normally while it runs — it's throttled to stay out of your way, and it only happens once (new games you add get handled automatically).
- Low-power (lite) build behavior is unchanged in this release.

### Downloads
- **eOr-v2.6.4-full.apk** — for most devices
- **eOr-v2.6.4-lite.apk** — for low-power handhelds / weak chipsets (RG DS RK3568 and similar)

Both APKs are signed with the same key as previous releases, so you can update in place. Thanks for bearing with me on this one. 🫏
