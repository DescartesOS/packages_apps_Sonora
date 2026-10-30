# Sonora phone verification

Build the universal mobile FOSS debug APK with `scripts/verify-sonora.sh`.
This runs assembly, JVM unit tests, and Android lint. Inspect the lint report:
the upstream build config sets `abortOnError = false`, so a successful Gradle
invocation alone does not establish that lint has no errors.

## Install

Enable USB debugging, connect and authorize the phone, then run:

```sh
adb devices -l
adb install -r artifacts/sonora-debug.apk
adb shell monkey -p moe.rukamori.archivetune.debug -c android.intent.category.LAUNCHER 1
```

The debug package ID is `moe.rukamori.archivetune.debug`. If an existing installation
uses a different signing certificate, Android will reject the update. Preserve
its data before deciding whether to remove it.

## Manual checks

- Launch, onboarding, and launcher/themed icon display Sonora correctly.
- Home: Explore opens Search; Your library opens Library; back and bottom
  navigation remain consistent; refresh and category filters work.
- Search: text entry, keyboard search, online/local results, and playback.
- Library: tabs, scrolling, playlists, downloads, sorting, and empty states.
- Player: play/pause, previous/next, seeking, queue, lyrics, collapse/expand,
  background playback, notification controls, and headset controls.
- Glass: navigation and mini-player stay legible over bright and dark artwork.
- Settings: grouped rows, toggles, dialogs, and existing saved choices work.
- Repeat in light/dark themes, pure black, large fonts, landscape, gesture
  navigation, and with system animations disabled.
- Check startup and playback with networking unavailable.

Capture a crash without clearing existing logs:

```sh
adb logcat -b crash -d > artifacts/sonora-crash.log
```

Visual appearance, touch behavior, actual audio, and GPU glass effects require
these device checks; JVM tests and lint cannot verify them.
