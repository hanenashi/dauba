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
- optional free canvas rotation;
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
- two-finger twist: rotate if rotation is enabled;
- comment tool: tap image -> place anchor -> enter note in a small sheet/editor.

Make accidental drawing while transforming the canvas difficult.

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
