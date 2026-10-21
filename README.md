# Anime Companion 🌸

Personal Android anime companion with animated characters, emotions and dances.

## First prototype

This repository contains the first Android prototype. It draws a temporary anime-style character in code so the overlay and interaction can be tested before adding the custom artwork and full animation assets.

### Current features
- Android app written in Kotlin.
- User-controlled floating overlay, displayed over other apps after permission is granted.
- Drag the character around the screen.
- Tap the character to change her expression.
- Start a simple bouncing dance from the app.
- Hide the companion by stopping the service.
- GitHub Actions workflow builds a debug APK.

### Build an APK on GitHub
1. Open the Actions tab in this repository.
2. Select Build Anime Companion APK.
3. Tap Run workflow if it has not started automatically.
4. Open the completed run and download the anime-companion-debug-apk artifact.
5. Extract the ZIP and install the APK on your Android phone. Android may ask you to allow installation from your browser or file manager.
6. Open Anime Companion, allow Display over other apps, and tap Позвать компаньонку.

The APK is a debug build for personal testing, not a Play Store release.

## Planned
- Replace the temporary drawing with prepared art based on the selected character reference.
- Smooth animation states: idle, blink, walk, sit, sleep, fall and several dances.
- Multiple character packs and a character selector.
- Personality, mood and simple local memory.
- Optional conversation feature researched separately.

## Notes
- Minimum Android version: Android 8.0 (API 26).
- Overlay permission is required for the floating character.
- Android battery management may stop background work on some devices; adjust battery settings if needed.
- No backend or paid API is required for this prototype.
