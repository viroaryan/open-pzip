# pzip

**A clear home for your Android files, archives, photos and videos.**

pzip brings file browsing, archive tools, a media player, gallery, storage cleanup and privacy controls into one Android app. It is built with Kotlin and Jetpack Compose.

## Features

| Area | What pzip does |
| --- | --- |
| Files | Browse, search, sort, select and organize local files. |
| Archives | Create ZIP and 7z archives; extract ZIP, RAR, 7z, TAR and other supported formats. |
| Media | Play video and audio with libVLC, subtitles, gestures and picture-in-picture. |
| Gallery | Browse images and open supported files with built-in viewers. |
| Cleanup | Review large files, duplicates and empty folders before removal. |
| Privacy | Optional PIN/biometric app lock, screenshot blocking, safe folder and Trash. |

## Get pzip

Download signed Android APKs from [official releases](https://github.com/viroaryan/pzip-releases/releases). The pzip website displays the latest published release and links directly to its APK assets.

## Build from source

Requirements: JDK 17, Android SDK platform 36, NDK 27.2.12479018. Set `sdk.dir` in untracked `local.properties`, then:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Debug APKs are in `app/build/outputs/apk/debug/`. Release builds require a signing key; see [RELEASING.md](RELEASING.md). No signing secrets are committed.

## Project layout

- `app/src/main/java/app/pzip/core` — file, archive and cleanup operations
- `app/src/main/java/app/pzip/player` — media playback
- `app/src/main/java/app/pzip/ui` — Compose interface
- `app/src/main/jni/unrar` — bundled UnRAR source, under its separate license

## People

Created by **Abhiraj Aryan**, owner of Nirvana Team, with co-founder **Rahul Kumar Pal**. The team also makes [Nirvana Browser](https://play.google.com/store/apps/details?id=org.nirvana).

## License

Original pzip source code is licensed under the [MIT License](LICENSE). Bundled third-party source and libraries retain their own terms; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and `app/src/main/jni/unrar/license.txt`. In particular, UnRAR is **not** MIT licensed.
