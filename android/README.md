# Cloudmoji Android

Native Kotlin/Jetpack Compose client for Android phones and tablets.

## Prerequisites

Install the current stable Android Studio from:

https://developer.android.com/studio/install

Let its Setup Wizard install the Android SDK, Platform Tools, Emulator, API 37
platform, and Build Tools 36.0.0. The build requires JDK 17; Android Studio's
bundled runtime is sufficient.

This Mac did not have Android Studio, an Android SDK, `adb`, an emulator, or a
working Java runtime when the scaffold was created.

## Open and run

Open this `android` directory as the Android Studio project:

```text
/Users/kevincjz/Programming/cloudmoji/android
```

Create an ARM64 Google Play phone AVD and press Run.

## Command line

Run these commands from this directory:

```bash
./gradlew assembleDebug
./gradlew test
./gradlew check
./gradlew installDebug
./gradlew connectedAndroidTest
```

`connectedAndroidTest` requires a running emulator or attached device.

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Shared content

The committed asset at `app/src/main/assets/EmojiData.json` is generated from
the repository's `src/data/*.ts` source of truth:

```bash
cd /Users/kevincjz/Programming/cloudmoji
npm run generate:android
npm run verify:android
```

Never hand-edit the generated Android JSON.

## Current scope

The scaffold contains the production app identity, Compose theme, launcher,
free/full access policy, route foundation, shared content asset, and smoke
tests. Mini-app screens are placeholders until the corresponding phases in the
Android implementation plan land.

See:

```text
docs/superpowers/plans/2026-07-30-android-app.md
```

