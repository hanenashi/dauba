# Dauba — handoff

## TL;DR

Dauba is a deliberately small native Android screenshot-annotation tool for giving visual UI-change instructions to Codex/other coding agents.

Core loop:

1. Take a real screenshot of the app being worked on.
2. Open/share it into Dauba.
3. Draw crude markup directly over the real screenshot.
4. Add anchored text comments.
5. Export a simple packet containing the original screenshot, annotated screenshot, and Markdown notes.
6. Codex reads that packet and changes the real app code.
7. Repeat with a fresh screenshot.

The real rendered app is the source of truth. Dauba is only the annotation layer.

## MVP

Keep it aggressively simple:

- native Android app, preferably Jetpack Compose;
- import/open screenshot from picker and Android Share;
- one annotation layer over the image;
- freehand brush;
- a few preset colors;
- a few brush sizes;
- eraser;
- undo / redo;
- two-finger pan and zoom;
- 90° clockwise / counterclockwise canvas rotation only (no free rotation in the first build);
- anchored text comments;
- export/share the result.

No fancy vector editor.

Specifically avoid, unless real use later proves otherwise:

- snapping;
- shape tools;
- auto smoothing;
- Bézier/path editing;
- alignment guides;
- layers panel;
- reusable design components;
- Figma-like layout tools;
- AI inside the app.

Feature restraint is part of the product.

## Comment model

Text comments should not be permanently painted into the bitmap.

A comment creates a simple visible anchor such as `A`, `B`, `C` on the annotation layer, with its text stored separately.

Example companion Markdown:

```md
# screen.png

A. Reduce vertical padding around this post.
B. Move the bookmark slightly left.
C. Make this reply box less prominent.
```

Coordinates can also be stored internally in a tiny project/JSON file if useful later, preferably normalized to the base image dimensions. The Markdown remains the human-readable instruction source.

## Export packet

Initial export should be boring and transparent, for example:

```text
screen.png
screen-annotated.png
screen.md
```

Optional later metadata:

```text
screen.json
```

The JSON is plumbing only: image dimensions, comment-anchor coordinates, perhaps stroke/project state. Do not make it necessary for a human to understand the packet.

## Interaction idea

The base screenshot never changes while editing.

Dauba maintains annotation/project state separately and flattens the visible result only on export.

Suggested touch behavior:

- one finger: draw with current tool;
- two fingers: pan / zoom;
- explicit buttons: rotate the working canvas 90° clockwise / counterclockwise;
- comment tool: tap image -> place anchor -> enter note in a small sheet/editor.

Make accidental drawing while transforming the canvas difficult.

## Visual / GUI direction after first dogfood pass

The first working build proved the editor useful, but the persistent app chrome takes too much of the viewport. The screenshot itself should dominate almost the entire screen.

Preferred direction:

### Canvas-first fullscreen editor

Default editing state should show almost only:

- the real screenshot;
- the annotation layer;
- at most one tiny persistent handle/pill/current-tool indicator.

While editing, hide normal status/navigation chrome where practical. Branding, filename, dimensions, large buttons, and explanatory copy do not belong in the permanent editor view.

When the user starts drawing, any visible tool chrome should disappear automatically and stay hidden until deliberately recalled.

### Quick tools overlay

A small temporary overlay/drawer should expose:

- Brush;
- Eraser;
- Note;
- Hand;
- current color / compact preset colors;
- brush size;
- undo / redo;
- Fit.

The current large tool cards are visually pleasant but consume too much space when always visible. They can survive as a temporary drawer/panel rather than permanent UI.

Possible access patterns worth testing:

- a thin edge tab/handle;
- tap empty canvas to show/hide controls;
- bottom swipe-up tool drawer.

Prefer one obvious mechanism over several clever gestures.

### Secondary menu / settings sheet

Move infrequent actions out of the main editor:

- Open / replace screenshot;
- Export;
- Notes list;
- rotate left/right;
- project/image info;
- Settings;
- About.

### Settings / About content

Keep this operational and small:

- app name: Dauba;
- motto: Paint what you mean;
- version name + build number;
- default brush color;
- default brush size;
- remember last tool;
- optional auto-hide controls;
- optional keep-screen-awake while editing;
- export packet summary;
- optional show/hide note labels on canvas;
- optional include JSON in export;
- source/repository link later.

Do not turn Settings into a theme/customization system.

### Guiding principle

Dauba should feel like a transparent markup surface over reality, not like a conventional editor wrapped around an image.

Default state: screenshot.
Temporary state: controls.
Rare state: settings / export / project management.

## Future repo-aware workflow

This is deliberately a later step, but the first useful direction is clear enough to record now.

The owner often works on several Codex-managed repositories. Dauba could know which project a screenshot belongs to and export packets using a repository-defined naming convention rather than arbitrary filenames.

### Tiny per-repository manifest

A repository may optionally contain a small committed manifest, for example:

```text
.dauba.json
```

Keep it human-readable and boring. Possible first shape:

```json
{
  "schema": 1,
  "project": "bokounapp",
  "displayName": "BokounApp",
  "packetPrefix": "bokoun",
  "defaultBranch": "main",
  "visualDir": "visual/dauba"
}
```

Do not put secrets, credentials, paths to private account data, or machine-specific absolute paths in it.

The exact schema is not fixed yet. Start only when a real repo-selection workflow is being built.

### Why a repo manifest may help

When Dauba chooses a repo/project, it could derive stable packet names such as:

```text
bokoun-board-2026-09-27-01.zip
bokoun-settings-2026-09-27-02.zip
```

or a folder/packet identity such as:

```text
visual/dauba/bokoun-board-2026-09-27-01/
```

Codex CLI could keep the manifest updated when project naming or workflow conventions change. Dauba would only consume the small public project metadata it needs.

Avoid inventing an elaborate taxonomy. Prefer short stable project IDs plus a free-form task/screen slug.

### Possible Dauba -> Codex -> commit loop

A useful future workflow may be:

1. Select repo/project in Dauba.
2. Annotate a real screenshot.
3. Export a Dauba packet.
4. Move/share the ZIP to the development machine or known repo inbox.
5. Codex CLI reads:
   - original screenshot;
   - annotated screenshot;
   - Markdown notes;
   - JSON coordinates/metadata;
   - repo `.dauba.json`.
6. Codex makes the requested code changes.
7. The Dauba packet is optionally retained alongside the commit as visual intent/evidence.

Possible retention approaches, in increasing order of permanence:

- keep packets outside Git and mention their local path in the Codex session;
- extract only `screen.md` plus selected images into `visual/dauba/`;
- commit the entire ZIP under `visual/dauba/`;
- attach the ZIP to a GitHub issue/release/artifact instead of bloating normal Git history.

Do not automatically commit ZIPs by default until real usage shows that this history is valuable. PNG-heavy packets can grow repositories quickly.

A nice eventual commit message/body convention could mention the packet ID, for example:

```text
Tighten board post spacing

Dauba: bokoun-board-2026-09-27-01
```

That gives human-readable traceability without making Dauba part of the application runtime.

### Repo selection in Dauba

If implemented, keep selection simple:

- a remembered list of known projects;
- display name + short ID;
- optional repo URL;
- no Git credentials inside Dauba;
- no direct Git operations required for the first version.

Dauba should not become a Git client. Codex/CLI remains responsible for reading repositories, editing code, testing, and committing.

## Possible later additions

Only after real usage demonstrates a need:

- non-destructive crop / focus region;
- export a crop as well as the full screenshot;
- before/after sessions where a new base screenshot can be compared against the previous one;
- opacity swipe/blink comparison;
- highlighter brush;
- simple arrow shortcut.

Do not add these to the first build merely because they are easy.

## Product philosophy

Dauba is not a design application.

It exists because rebuilding a real Compose UI in Figma or another design tool creates an approximation that can drift from reality. Dauba instead keeps the actual app screenshot as the visual truth and lets the owner literally paint/write requested changes on top of it.

Working motto:

> Paint what you mean.

Package/repo naming can simply use `Dauba` / `dauba`.
