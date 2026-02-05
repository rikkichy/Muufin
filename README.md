<p align="center">
  <img
    src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
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

<p align="center">
<img alt="GitHub Downloads (all assets, all releases)" src="https://img.shields.io/github/downloads/rikkichy/Muufin/total?style=for-the-badge&labelColor=2D3142&color=B0D7FF">
<img alt="GitHub Actions Workflow Status" src="https://img.shields.io/github/actions/workflow/status/rikkichy/Muufin/main.yml?style=for-the-badge&labelColor=2D3142&color=B0D7FF">
<img alt="GitHub License" src="https://img.shields.io/github/license/rikkichy/Muufin?style=for-the-badge&labelColor=2D3142&color=B0D7FF">
</p>

---

<h2 align="center">Showcase</h2>
<table align="center">
  <tr>
    <td align="center">
      <img
        src="https://github.com/rikkichy/assets/blob/main/Muufin/muufin_mainscreen.jpeg?raw=true"
        alt="Muufin main screen"
        width="220"
      />
      <br />
      <strong>Library</strong>
    </td>
    <td align="center">
      <img
        src="https://github.com/rikkichy/assets/blob/main/Muufin/muufin_nowplaying.jpeg?raw=true"
        alt="Muufin now playing screen"
        width="220"
      />
      <br />
      <strong>Now Playing</strong>
    </td>
  </tr>
</table>

---
## Download

Head to the **[Releases](../../releases)** page and download the latest apk.

> [!CAUTION]
> Alpha builds may be unstable and incompatible between versions.<br> Use at own risk.

## Features
- Material Expressive UI
- DirectPlay FLAC support
- Playback Reporting (AKA Private Mode), [LastFM support](https://github.com/jesseward/jellyfin-plugin-lastfm)
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

## Build from source

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

