# Native Appearance

**Settings > Appearance** styles MagicDesk's taskbar, Start, panels and built-in
tools. It is available without Desktop, shell access or Termux. Dark, Light and
Contrast select colors, typography and control shapes while preserving taskbar
geometry, composition, symbols and motion. Reset restores the complete shell
configuration. Import and Export use Android's document picker and one JSON document.
Import, **Edit configuration** and **Taskbar components** offer a live preview with
**Keep changes** or cancellation. Closing the confirmation restores the committed
configuration. Process restart also discards an unconfirmed preview.

The setting does not change Android application captions, third-party apps,
wallpaper, widget bindings or terminal protocol colors. **System theme during
Desktop** is a separate, temporary system-wide preference.

## Ownership

`ShellAppearance` is an immutable density-independent value: semantic palette,
typography, control shape, taskbar layout, `ShellComposition`, `ShellMotion`,
feedback states and symbolic `ShellResources`. `AppearanceStore` owns app-private
global defaults, independent of privileged storage and workspace lifetimes.
No ephemeral display ID is persisted. Every workspace resolves layout using its
current viewport and density, including a portable workspace moved to another
output.

`UiColor` identifies purpose rather than a literal paint value. `UiAppearance`
binds native Views and drawables to these roles. Updates preserve the existing
Views, focus, text selection, scroll, tool state and PTYs for style-only changes.
Composition changes reconcile taskbar children and rebuild Start contents while
preserving its search text and selection. Registrations are weak;
workspace listeners are released with their owner. Changes run on the main
thread, without a rendering/pointer polling loop. Terminal content retains its
own font and palette; its Android toolbar follows the shell style.

`TaskbarGeometry` resolves size constraints and edge-reveal placement.
`DesktopShellLayout` contributes the resulting intent to the workspace's shared
`ShellLayout`. The Android host consumes its full content and paint rectangles;
it does not recompute insets or reserve space independently. Color changes grant
no input or focus authority and do not change Android task-area topology.

## Document

Documents are at most 32 KiB, version 2, with bounded nesting. Optional fields
inherit the named built-in `preset` (default `dark`), not the previously selected
theme. Unknown fields, invalid types and out-of-range values are rejected before
any setting changes. `ShellAppearanceSchema` publishes the schema used by the
validator; errors identify a JSON-pointer path and the permitted values. Obtain
it through **Export JSON Schema**, `appearance.schema`, or the MCP resource
`magicdesk://appearance/schema`. Component identity and host-specific constraints
are additionally validated by the typed model. External paths, scripts and binary
assets are not accepted.

```json
{
  "version": 2,
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
  },
  "composition": {
    "taskbar": [
      { "type": "start" },
      { "type": "tasks" },
      { "type": "quick_controls" },
      { "type": "clock" }
    ],
    "start": { "sections": ["apps", "recent", "tools"], "presentation": "grid" }
  },
  "motion": { "panels": "fade", "taskbar": "fade", "durationMs": 160 }
}
```

Color roles are `background`, `panel`, `surface`, `text`, `muted`, `accent`,
`danger`, `attention`, `hover`, and `desktop_text`, written as opaque `#RRGGBB`.
The desktop label color is separate from panel text because wallpaper is independent.
Taskbar background
opacity is independent of icons and labels. App icon artwork is left intact.
By default, taskbar controls and Start entries have no permanent individual background;
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

## Composition

`composition.taskbar` is an ordered list of 1-24 native components: `start`,
`tasks`, `show_desktop`, `open_tasks`, `notifications`, `keyboard_layout`,
`phone_screen`, `quick_controls`, `battery`, `clock`, `spacer`. Components are
singletons except spacers. Omitted components do not close their services or
change workspace tasks. Actions retain their production controllers and semantic
automation identities.

Each component accepts `widthDp` (0 for automatic, otherwise 32-240),
`minViewportDp` (0-4096), and `visibility` (`always`, `expanded`, `external`).
Conditions use the output's logical width and compact-preview status, not an
animation frame or pointer event. The phone-screen action additionally requires
its existing runtime availability. `start.label` overrides its localized label;
`clock.clock` selects `time`, `date`, or `date_time`.

Automatic tasks and spacers share spare width. Fixed controls retain their
dimensions; the component strip scrolls when it cannot fit. Task entries retain
their separate overflow menu. `TaskbarController` reconciles the committed list
while retaining component Views and service bindings; palette changes do not
rebuild the list. `ShellComponentLayout` owns allocation without Android objects.
The native host is one horizontal bottom taskbar. Composition does not create
arbitrary Android windows, additional bars or privileged layers.

`composition.start` selects ordered `sections` (`recent`, `apps`, `running`,
`tools`), `presentation` (`grid`, `list`), `tileWidthDp` (80-200), and `iconSizeDp`
(24-64). `apps` is required and sections cannot repeat. Each Start host omits
unavailable sections without creating Desktop services. Both presentations use
the shared application catalog and bounded pages. Search, launch destinations,
recent scope and application identity remain owned by their existing services.

## Feedback And Resources

`feedback` maps `normal`, `hover`, `pressed`, `selected`, `focused`, `disabled`
and `outline` to palette roles, including `transparent`. Native flat controls
share one retained state drawable. Defaults have no idle backplate and outline
only selection or keyboard focus; state changes retain their geometry.

`resources.icons` maps semantic symbols to bundled symbols: `desktop`, `windows`,
`notifications`, `keyboard`, `controls`, `files`, `terminal`, `settings`, `search`,
`camera`, `video`. Android resource IDs are not part of the document. These are
single-step substitutions, not recursive aliases. Application artwork remains
owned by the application catalog.

## Motion

`motion.panels` and `motion.taskbar` accept `none` or `fade` for native popup
appearance and taskbar reveal. `durationMs` is 0-400; `curve` is `linear`,
`ease_out` or `smooth`. `feedbackMs` (0-250) controls native flat-control state
transitions. `reduced` disables these effects, as does Android's disabled animator
setting. Default effects are off.

`UiMotion` changes presentation opacity only. It never delays focus, changes
layout reservations, or submits a task/window transaction. Hiding and detaching
cancel effects immediately; theme replacement cancels active effects. It does
not animate application tasks, external Linux surfaces, or display topology.

## Automation

MCP and the generated CLI use the same store and validator:

- `appearance.schema`: published JSON Schema; observation.
- `appearance.validate`: validate and resolve `document` without applying it; observation.
- `appearance.get`: effective and committed documents, revision, preview ID and presets; observation.
- `appearance.apply`: replace with `document`, superseding an active preview; control permission.
- `appearance.preview`: apply without persistence, returning an exact `previewId`; control permission.
- `appearance.confirm` / `appearance.cancel`: commit or discard that exact preview; control permission.
- `appearance.preset`: apply `name` without changing geometry; control permission.
- `appearance.reset`: restore default style and geometry; control permission.

`AppearanceTransaction` retains one process-local preview. Overlapping previews
are rejected. A stale ID cannot undo a later apply or another preview. Confirmed
preferences use the normal asynchronous app-private preferences persistence;
preview state is never persisted. Results identify accepted configuration, not
an acknowledgement of displayed pixels or completed disk I/O.
These operations do not start Desktop or acquire display input.

Example documents: [compact dock](themes/compact-dock.json),
[light workspace](themes/light-workspace.json), [quiet controls](themes/quiet-controls.json).
