# Sazanami

Sazanami is an in-progress Android music player for a local personal music library. It scans music from the device with MediaStore, displays songs by song, artist, and album, and plays audio through a Media3 playback service.

The app is fully functional and actively in development. Core playback and library browsing are complete: you can listen to your local library by song, artist, or album, with shuffle, repeat, queue management, and persistent playback state. Planned features still in progress include favorites, playlists, Android Auto support, and additional playback polish.
## Current Features

- Browse local songs from device media storage.
- View the library by songs, artists, and albums.
- Search and sort library views.
- Select which detected music folders are included in the library.
- Play, pause, skip, seek, shuffle, and repeat.
- Manage an Up Next queue with play-next, add-to-queue, reorder, remove, and clear actions.
- Use a mini player and expanded now-playing screen.
- Persist selected folders and playback state.

## Current Status

This is a work in progress. The app currently reads song metadata from MediaStore and stores lightweight app state with SharedPreferences. A Room database foundation has been added, but it is not yet wired into a user-facing feature or real music-library persistence.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX Media3 / ExoPlayer
- Coil Compose
- Room
- KSP
- Gradle Kotlin DSL

## Project Structure

```text
app/src/main/java/io/github/rsgarrido/sazanami/
  MainActivity.kt              App entry point and top-level wiring
  data/                        Song models, library preferences, MediaStore repository
  data/local/                  Room database scaffold
  player/                      Media3 service, playback wrapper, playback controller/state
  ui/                          Compose screens and reusable UI pieces
  ui/theme/                    Compose theme, color, and typography setup
```

## Build

From the project root:

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS/Linux:

```bash
./gradlew :app:assembleDebug
```

## Permissions

The app requests media permissions for audio and images so it can read music files and nearby album artwork from device storage. It also declares foreground service permissions for media playback.

## Notes

- Tests are currently starter/template tests only.
- Release minification is disabled.
- The Room database currently contains a marker entity and provider scaffold.
- The app name is still using the package/example namespace in several places, so polish and hardening are still ahead.
