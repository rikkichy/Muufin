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
  Minimal cross-platform music client for <strong>Jellyfin</strong>, built with <strong>Flutter</strong>.
</p>

<p align="center">
  Project is in <strong>Alpha</strong>, things will break and features may change.
</p>



---

## Features
- Browse your library in Material You UI, both on Android and Windows
- Lossless FLAC support
- “Now Playing” screen with playback controls
- Global search

---

## Planned
- [ ] Ability to download playlists
- [ ] Search and add with Lidarr
- [ ] Account and instance management
- [ ] Advanced settings
- [ ] Cast button
- [ ] Internal and external lyrics support

---
## Download

Head to the **[Releases](../../releases)** page:

- **Android** → download the latest `.apk`
- **Windows** → download the latest Windows build

> Alpha builds may be unstable and incompatible between versions.

---

## Build from source

### Requirements

- [Flutter](https://flutter.dev/) installed and configured
- A running Jellyfin server
- Android Studio, NDK & SDK
- Tools from Visual Studio

### Clone & build

```bash
git clone https://github.com/rikkichy/Muufin.git
```
```
cd Muufin
```
```
flutter pub get
```
### Android
```bash
flutter build apk --release
```
### Windows
```bash
flutter build windows --release
```
