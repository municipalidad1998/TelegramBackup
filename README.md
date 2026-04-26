# Telegram Backup - Android App

A complete Android application for automatic file backup to Telegram using Bot API.

## Features

### 🔄 Automatic Backup
- Auto-detect new files (photos, videos, music, documents)
- Upload only on WiFi (configurable)
- SHA-256 hash deduplication
- Persistent tracking via Room database
- Background upload with WorkManager

### 🖼️ Gallery
- Timeline-based gallery view
- Filter by file type (photos, videos, documents)
- Image viewer with pinch-to-zoom
- Files remain visible even after deletion from device (available on Telegram)

### 🎬 Video Player (HBO Max style)
- Custom controls: ±5s seek, play/pause
- Playlist support
- Mini-player mode
- Stream directly from Telegram

### 🎵 Audio Player (Spotify style)
- Modern audio UI with gradient artwork
- Background playback via Media3 service
- Persistent notification controls
- Create custom playlists
- Add songs to playlists

### ⚙️ Smart Features
- WiFi-only upload mode
- Auto-backup scheduling (15 min intervals)
- Upload progress tracking
- Error handling with retry
- Dark mode support

### 🔁 Continuity System
- Survives uninstall/reinstall
- SHA-256 hash prevents duplicate uploads
- Database stores Telegram file IDs
- Re-download from Telegram when local file missing

## Architecture

- **Kotlin** + **Jetpack Compose** (Material 3)
- **MVVM** with Clean Architecture
- **Hilt** for dependency injection
- **Room** for local database
- **WorkManager** for background tasks
- **Media3 / ExoPlayer** for media playback
- **OkHttp** for Telegram API
- **Coil** for image loading
- **DataStore** for preferences

## Setup

1. Get a Telegram Bot Token from [@BotFather](https://t.me/BotFather)
2. Get your Chat ID from [@userinfobot](https://t.me/userinfobot)
3. Enter both in the app's setup dialog
4. Grant storage permissions when prompted
5. Tap "Scan" to discover files
6. Tap "Upload All" to start backup

## Build

```bash
# Clone
git clone https://github.com/municipalidad1998/TelegramBackup.git
cd TelegramBackup

# Build debug APK
./gradlew assembleDebug

# APK location
# app/build/outputs/apk/debug/app-debug.apk
```

## Requirements

- Android 8.0+ (API 26)
- Telegram Bot Token
- Storage permissions

## File Size Limit

Telegram Bot API limits:
- Photos: 10 MB
- Videos/Documents: 50 MB

## License

MIT
