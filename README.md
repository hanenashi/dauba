# Dauba

Paint what you mean. A small native Android screenshot annotation app for giving visual UI-change instructions to coding agents.

[Download the first test APK](https://github.com/hanenashi/dauba/releases/tag/v0.1.0).
Android 10 or newer. No account, network permission, or broad storage permission.

## First test

Download `dauba-0.1.0-test.apk` from the GitHub release on your phone and open it. If Android asks, allow your browser or Files app to install this app. This works away from home; no ADB or Wi-Fi pairing is required.

1. Open a screenshot with the picker, or share an image to **Dauba** from another app.
2. Draw with one finger. Choose from six colors and three brush sizes.
3. Use **Note** and tap the image to place A, B, C… anchors. Tap an existing anchor with the Note tool, or use the notes list, to edit/delete its text.
4. Pan and zoom with two fingers, or pan with the Hand tool. Adding a second finger cancels the tentative draw/erase. The rotation buttons turn the canvas exactly 90° clockwise/counterclockwise; **Fit** resets pan/zoom.
5. **Export → Share ZIP** sends a packet to another app. **Save ZIP** uses Android's file picker so you can save to Downloads or another document provider.

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

## Code map

- `MainActivity.kt`: Compose interface, image picker, incoming shares, export actions.
- `AnnotationCanvas.kt`: native touch surface, image transform, shared annotation renderer.
- `Project.kt`: immutable strokes/notes, undo/redo, hit testing, Markdown.
- `ProjectStore.kt`: private image copy, atomic saved state, lossless PNG/ZIP export.
- `EditorModel.kt`: editor state and serialized background storage/export operations.

The original product brief is in [handoff.md](handoff.md).
