# Apple Music Local Player

A native Android (Java) local music player, styled after Apple Music, that
plays audio files already stored on the device — no streaming, no accounts,
no network access required at runtime.

## Features

- Browse your entire local audio library (read via `MediaStore`)
- Search by title, artist, or album
- Favorites, with a dedicated tab
- Shuffle and repeat (off / repeat-all / repeat-one)
- Full-screen Now Playing screen with **swipe-to-change-track**
- Background playback via a foreground `Service`
- System **MediaSession** integration (lock screen, Bluetooth, wearables,
  Android Auto, etc.)
- A persistent, glanceable **MediaStyle notification** with big album art and
  play/pause/skip actions — the platform-native equivalent of a "Dynamic
  Island"-style live activity for media playback

## Project layout

```
.
├── app/                          # The Android app module
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/applemusicplayer/
│       │   ├── MainActivity.java         # Library / search / favorites
│       │   ├── NowPlayingActivity.java   # Swipeable full-screen player
│       │   ├── PlayerApplication.java
│       │   ├── adapter/                  # RecyclerView & ViewPager2 adapters
│       │   ├── model/                    # Song.java
│       │   ├── playback/MusicService.java# MediaSession + notification + player
│       │   └── util/                     # Library loading, favorites, queue
│       ├── assets/index.html             # Bundled "about" page
│       └── res/                          # Layouts, drawables, strings, themes
├── gradle/wrapper/                # Gradle Wrapper (Gradle 8.7)
├── settings.gradle
├── build.gradle
├── gradle.properties
└── .github/workflows/build-apk.yml
```

## Building

This project has **no dependency on Android Studio**. It builds entirely from
the command line using the Gradle Wrapper, which is exactly what GitHub
Codespaces and GitHub Actions do.

```bash
./gradlew assembleDebug
```

The resulting APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

### Requirements

- JDK 17
- Gradle 8.7 (via the checked-in wrapper — no local Gradle install needed)
- Internet access on first build, so Gradle can download the Android Gradle
  Plugin, the Android SDK components it needs, and your dependencies

### Building in GitHub Codespaces

1. Open this repository in a Codespace.
2. Run `./gradlew assembleDebug` in the terminal.
3. Grab the APK from `app/build/outputs/apk/debug/app-debug.apk`.

### Continuous Integration

Every push and pull request triggers `.github/workflows/build-apk.yml`, which:

1. Checks out the repo
2. Installs JDK 17 and the Android SDK
3. Provisions Gradle 8.7 and refreshes the wrapper to match
4. Runs `./gradlew assembleDebug`
5. Uploads `app-debug.apk` as a workflow artifact

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (below) | Read the local audio library |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep music playing while the app is backgrounded |
| `POST_NOTIFICATIONS` | Show the playback notification on Android 13+ |
| `WAKE_LOCK` | Keep the CPU awake during active playback |

## License

MIT — see [LICENSE](LICENSE).
