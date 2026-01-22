<p align="center">
  <img
    src="assets/muufin.png"
    alt="Muufin logo"
    width="160"
    style="border-radius:50%;"
  />
</p>


<h1 align="center">Muufin</h1>

<p align="center">
  Minimal Jellyfin music client for <strong>android</strong>, built with <strong>Material Expressive</strong>.
</p>

<p align="center">
  Project is in <strong>Alpha</strong>, things will break and features may change.
</p>



---

## Features
- Material Expressive UI, both on Android and Windows
- DirectPlay FLAC support
- Playback Reporting, [LastFM support](https://github.com/jesseward/jellyfin-plugin-lastfm)
- Now Playing screen with playback controls
- Global search across your music library

---

## Planned
- [ ] Ability to download playlists
- [ ] Search and add with Lidarr
- [x] Account and instance management
- [x] LastFM plugin support
- [ ] Internal and external lyrics support
> [!IMPORTANT]
> This list is not final, something may be removed/added or discontinued.
---
## Download

Head to the **[Releases](../../releases)** page and download the latest apk.

> [!CAUTION]
> Alpha builds may be unstable and incompatible between versions.<br> Use at own risk.
---

## Build from source

> [!WARNING]
> Flutter builds are discontinued. Use [Android Studio](https://developer.android.com/studio) to build and test.

```bash
git clone https://github.com/rikkichy/Muufin.git
```
---

## FAQ

**Q:**  Why are my song or album covers pixelated? <br>
**A:** Muufin does not compress or downscale images. Low-quality covers usually come directly from **your instance.**
> [!TIP]
> Go to your browser, type your instance address and play a song. Click on the song cover on the mini player, then right click full song cover and choose "Copy image address"<br> You'll get a link like this: https://jf.example.local/Items/123123/Images/Primary?maxHeight=600&tag=123123&quality=90<br> Remove what's coming after ? and paste in your browser.<br> If image is pixelated, that's because you've downloaded a song with low-quality artwork embedded.

**Q:** Muufin supports self-signed certs?<br>
**A:** **Yes.** You can import your .crt or just disable TLS verification without importing anything.

