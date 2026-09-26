# Dauba

Paint what you mean. A small native Android screenshot annotation app for giving visual UI-change instructions to coding agents.

[Download the fullscreen test APK](https://github.com/hanenashi/dauba/releases/tag/v0.2.0).
Android 10 or newer. No account, network permission, or broad storage permission.

## First test

Download `dauba-0.2.0-test.apk` from the GitHub release on your phone and open it. If Android asks, allow your browser or Files app to install this app. It updates the earlier preview and preserves your current screenshot and markup. This works away from home; no ADB or Wi-Fi pairing is required.

1. Open a screenshot with the picker, or share an image to **Dauba** from another app.
2. The editor fills the screen. Tap the small tool pill at the bottom-right to open the tray: Brush, Eraser, Note, Hand, colors, sizes, Undo/Redo, and Fit.
3. Draw with one finger. Starting a canvas gesture hides the tray without moving the screenshot or discarding the first stroke. The pill also disappears while your finger is down; it returns when the gesture ends, while the tray stays closed.
4. Use **Note** and tap the image to place A, B, C… anchors. Tap an existing anchor with the Note tool, or use the notes list, to edit/delete its text.
5. Pan and zoom with two fingers, or pan with the Hand tool. Adding a second finger cancels the tentative draw/erase. **Fit** resets pan/zoom.
6. The tray's **•••** menu holds Export, Notes, Open/replace, 90° clockwise/counterclockwise rotation, screenshot information, and Help/About.
7. **Export packet → Share ZIP** sends a packet to another app. **Save ZIP** uses Android's file picker so you can save to Downloads or another document provider.

Android's status/navigation bars hide while editing. Swipe from a screen edge to reveal them temporarily. Back closes the open tool tray. The canvas stays fixed when tools or system bars appear; it never shrinks to make room for them.

Each ZIP contains:

```text
screen.png              original screenshot, losslessly decoded to PNG
screen-annotated.png    full-resolution image with strokes and lettered anchors
screen.md               human-readable notes, matched by letter
screen.json             image dimensions, strokes, and anchor coordinates
```

Canvas rotation affects viewing only. Both exported images retain the original orientation and dimensions regardless of pan, zoom, or rotation. Note text stays in Markdown; only lettered anchors are painted into the annotated PNG. JSON coordinates are image pixels, with normalized coordinates also included for anchors.

## Deliberate limits

- One current project, automatically saved in private app storage. Export before replacing it with a new screenshot; the app asks before replacing existing work.
- Whole-stroke eraser. Undo/redo covers strokes, erasing, and note creation/editing/deletion. The last 100 undo states live in memory; reopening after process death restores the latest drawing, not its undo history.
- Imports above 20 megapixels are rejected without replacing the current screenshot. Animated images use a still frame.
- Exported ZIPs are delivery packets; reopening ZIPs as editable projects is not yet supported.
- No free rotation, shapes, smoothing, crop, layers panel, or in-app AI.

## Build and test

Use Java 21 and Android SDK platform `android-37.0` / build tools `37.0.0`. Set `ANDROID_HOME` or create an ignored `local.properties` with `sdk.dir=…`.

```sh
./gradlew testDebugUnitTest lintRelease assembleRelease
```

The small, optimized test APK is at `app/build/outputs/apk/release/app-release.apk`. The unoptimized developer build is available with `./gradlew assembleDebug`.

Preview APKs use the builder's persistent Android debug signing key outside the repository (`~/.android/debug.keystore`). Published GitHub releases are built on the same machine so later previews can update the existing installation. Preserve that key. GitHub Actions checks every push/PR and uploads a test APK, but its temporary signing key is different: prefer **Releases** for phone installs and updates.

Unit/Robolectric tests cover history, erasing, stable letter labels, Markdown, saved project state, ZIP/image content, cancellation of drawing during pinch gestures, and touch coordinates through all four quarter-turns. ImageDecoder import is exercised on an Android emulator because its file-descriptor JNI is not supported by the host test runtime.

Fullscreen instrumented tests verify unchanged canvas geometry/rendering when opening the tray, first-stroke delivery while controls auto-hide, immersive system bars, undo, note editing with the keyboard, and menu/rotation/export access. Run them only on an emulator with disposable app data; they deliberately skip physical devices to protect existing projects:

```sh
./gradlew assembleDebug assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r com.hanenashi.dauba.test/androidx.test.runner.AndroidJUnitRunner
```

## Code map

- `MainActivity.kt`: Compose interface, image picker, incoming shares, export actions.
- `EditorChrome.kt`: temporary tool tray, tool pill, and secondary menu actions.
- `AnnotationCanvas.kt`: native touch surface, image transform, shared annotation renderer.
- `Project.kt`: immutable strokes/notes, undo/redo, hit testing, Markdown.
- `ProjectStore.kt`: private image copy, atomic saved state, lossless PNG/ZIP export.
- `EditorModel.kt`: editor state and serialized background storage/export operations.

The original product brief is in [handoff.md](handoff.md).
