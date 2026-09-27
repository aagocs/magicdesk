# Native Appearance

**Settings > Appearance** styles MagicDesk's taskbar, Start, panels and built-in
tools. It is available without Desktop, shell access or Termux. Dark, Light and
Contrast select colors, typography and control shapes while preserving taskbar
geometry. Reset restores both style and geometry. Import and Export use Android's
document picker and one JSON document.

The setting does not change Android application captions, third-party apps,
wallpaper, widget bindings or terminal protocol colors. **System theme during
Desktop** is a separate, temporary system-wide preference.

## Ownership

`ShellAppearance` is an immutable density-independent value: semantic palette,
typography, control shape and taskbar layout. `AppearanceStore` owns app-private
global defaults, independent of privileged storage and workspace lifetimes.
No ephemeral display ID is persisted. Every workspace resolves layout using its
current viewport and density, including a portable workspace moved to another
output.

`UiColor` identifies purpose rather than a literal paint value. `UiAppearance`
binds native Views and drawables to these roles. Updates preserve the existing
Views, focus, text selection, scroll, tool state and PTYs. Registrations are weak;
workspace listeners are released with their owner. Changes run on the main
thread, without a rendering/pointer polling loop. Terminal content retains its
own font and palette; its Android toolbar follows the shell style.

`TaskbarGeometry` resolves size constraints and edge-reveal placement.
`DesktopShellLayout` contributes the resulting intent to the workspace's shared
`ShellLayout`. The Android host consumes its full content and paint rectangles;
it does not recompute insets or reserve space independently. Color changes grant
no input or focus authority and do not change Android task-area topology.

## Document

Documents are at most 32 KiB, version 1, with bounded nesting. Optional fields
inherit the named built-in `preset` (default `dark`), not the previously selected
theme. Unknown fields, invalid types and out-of-range values are rejected before
any setting changes. External paths, scripts and assets are not accepted.

```json
{
  "version": 1,
  "preset": "dark",
  "colors": { "accent": "#22D3EE" },
  "typography": { "font": "sans", "scale": 1 },
  "shape": { "radiusScale": 1, "borderDp": 1 },
  "taskbar": {
    "width": "content",
    "alignment": "center",
    "maxWidthDp": 1100,
    "sideGapDp": 12,
    "bottomGapDp": 12,
    "paddingDp": 8,
    "radiusDp": 8,
    "opacity": 0.88,
    "reserveSpace": true
  }
}
```

Color roles are `background`, `panel`, `surface`, `text`, `muted`, `accent`,
`danger`, `attention`, `hover`, and `desktop_text`, written as opaque `#RRGGBB`.
The desktop label color is separate from panel text because wallpaper is independent.
Taskbar background
opacity is independent of icons and labels. App icon artwork is left intact.
Taskbar controls and Start entries have no permanent individual background;
hover, press, keyboard focus and selection provide feedback. Running tasks use
an underline, stronger for the active task.

Fonts: `sans`, `serif`, `mono`; text scale: 0.8-1.3. The default font preserves
purpose-specific faces such as monospaced logs. Control radius scale: 0-2;
border: 0-3 dp. Taskbar width is `fill` or `content`; alignment is `start`,
`center`, or `end`. Maximum content width is 240-4096 dp, gaps 0-96 dp, padding
0-16 dp, radius 0-32 dp, opacity 0.15-1. Limits are constrained by the available
viewport and the minimum width of taskbar controls; excess applications use the
existing overflow menu. Geometry uses dp, never physical display DPI as storage.

With `reserveSpace`, windows avoid the panel and its bottom gap. The shared
model retains the precise edge interval, while rectangular window consumers
conservatively exclude a full-width band. An auto-hiding reserving taskbar only
reserves space for shell panels. Without reservation it overlays the workspace.
Gaps lie outside the Android input window. A flush full-width phone panel extends
its paint through the navigation inset; a floating panel does not. The hidden
reveal strip remains at the output's bottom edge, aligned with the panel's width.
Start, Quick controls, Calendar and notification panels follow the taskbar's live
surface through the shared popup placement policy, including overlay layouts.

## Automation

MCP and the generated CLI use the same store and validator:

- `appearance.get`: current resolved document and preset names; observation.
- `appearance.apply`: replace with `document`; control permission.
- `appearance.preset`: apply `name` without changing geometry; control permission.
- `appearance.reset`: restore default style and geometry; control permission.

Results contain the stored document, not an acknowledgement of displayed pixels.
These operations do not start Desktop or acquire display input.
