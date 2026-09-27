# Native Appearance

**Settings > Appearance** configures MagicDesk's native panels, Start and built-in
tools without starting Desktop or acquiring display input. Choose **Global
defaults**, or **Current workspace** when the host supplies a stable workspace
identity. **Use global defaults** removes that workspace's override. Dark, Light
and Contrast change colors, typography, shapes and feedback while retaining
backdrops, panel geometry, composition, resources and motion.

**Common background** sets background opacity (15-100%) and blur radius (0-64 dp)
for native shell panels, popup backgrounds and appearance-bound dialogs in the
selected scope. Select a panel to edit its edge, length, alignment, gaps,
thickness, padding, rounding and space reservation. **Use common background**
inherits the common opacity and blur;
uncheck it to edit both panel-specific sliders, initially set to the common
values. Checking it again removes the panel override. Add or remove panels,
reorder components, or move a component to another panel. Moving the last
component leaves a spacer in its source panel. Removing a panel removes its
components from the composition, not their underlying services or workspace tasks.

JSON and theme ZIP import/export use Android's document picker. Imports,
**Edit configuration** and **Panel components** offer a live preview with **Keep
changes** or cancellation. Dismissing confirmation restores committed state.
Closing Settings cancels its preview and pending work; process restart discards
unconfirmed previews. Edits and imports capture scope and revision so a late
result cannot overwrite a newer configuration or a different workspace.

Appearance does not restyle third-party applications, arbitrary Android dialogs,
Android captions or terminal protocol colors. Widget bindings, Android
application identities and permissions are independent. **System theme during
Desktop** is a separate, temporary system-wide preference.

## Ownership And Scope

`ShellAppearance` is an immutable density-independent model: semantic palette,
typography, shape, backdrop, composition, motion, feedback and resources.
`AppearanceStore` owns global defaults and workspace patches in app-private preferences.
`WorkspaceAppearance` resolves patches over global defaults. Stable profile or
workspace keys survive output changes; transient display IDs and live workspace
residency IDs are not persistence keys. Ordinary tools use global defaults unless
their host explicitly supplies a workspace binding.

Global documents inherit omitted fields from the named built-in `preset`
(default `dark`). A workspace patch inherits omitted fields from the global
document, not from its previous patch, and does not accept `preset`. Objects merge
recursively; arrays replace whole lists. Overriding `composition.panels` owns the
complete panel list, while overriding `typography.scale` still inherits the global
font. Likewise, a workspace edit to `backdrop.opacity` leaves `blurRadiusDp`
inherited, and a blur-radius edit leaves opacity inherited. Panel backdrop edits
replace the panel list without overriding the root common backdrop in the
workspace patch. Empty `{}` restores inheritance. There are at most 32 saved/preview scopes,
keys of at most 512 UTF-8 bytes and 256 KiB total override/preview JSON.

Native bindings use already-prepared immutable assets. File access and decoding
run on workers, never in draw callbacks. Style-only changes retain native Views,
focus, text selection, tool state and PTYs. Composition changes reconcile native
panel contents. Listeners and context bindings are released by their owners.
No custom renderer, script, executable or polling loop is part of a theme.

Panels contribute geometry and edge reservations to the existing shared
`ShellLayout`. The native hosts consume that layout without acquiring new task,
focus or input authority. Application task-area topology remains unchanged.

## Document

Documents and workspace patches are at most 32 KiB with bounded nesting. The
current document version is **4**. Unknown fields, invalid types, duplicate
identities and out-of-range values are rejected before the appearance changes.
The authoritative schema is available through **Export JSON Schema**,
`appearance.schema`, and `magicdesk://appearance/schema`. Errors identify
JSON-pointer paths; typed model checks also enforce cross-panel uniqueness.

```json
{
  "version": 4,
  "preset": "dark",
  "colors": { "accent": "#22D3EE" },
  "backdrop": { "opacity": 0.9, "blurRadiusDp": 16 },
  "composition": {
    "panels": [
      {
        "id": "dock",
        "edge": "bottom",
        "style": {
          "length": "content",
          "alignment": "center",
          "maxLengthDp": 1100,
          "sideGapDp": 12,
          "edgeGapDp": 12,
          "thicknessDp": 0,
          "paddingDp": 8,
          "radiusDp": 8,
          "backdrop": { "opacity": 0.88, "blurRadiusDp": 24 },
          "reserveSpace": true
        },
        "components": [ { "type": "start" }, { "type": "tasks" } ]
      },
      {
        "id": "status",
        "edge": "top",
        "components": [ { "type": "quick_controls" }, { "type": "spacer" }, { "type": "clock" } ]
      }
    ],
    "start": { "sections": ["apps", "recent", "tools"], "presentation": "grid" }
  },
  "motion": { "panels": "fade", "taskbar": "fade", "durationMs": 160 }
}
```

Color roles are `background`, `panel`, `surface`, `text`, `muted`, `accent`,
`danger`, `attention`, `hover` and `desktop_text`, written as opaque `#RRGGBB`.
Desktop label color is independent of panel text. Background opacity does
not fade icons or labels. Application artwork stays owned by the application
catalog. Fonts are `sans`, `serif` or `mono`, with scale 0.8-1.3. Control radius
scale is 0-2 and border width 0-3 dp.

## Backdrops

The root `backdrop` supplies the common background for native shell panels and
popup backgrounds through their Window bindings. Dialogs explicitly styled through
`UiAppearance.dialog`, including Appearance settings and panel-owned dialogs,
use the same backdrop; unrelated `AlertDialog` instances are not automatically
themed. It contains `opacity` (0.15-1, default 1) and `blurRadiusDp` (integer
0-64, default 0). A panel's optional `style.backdrop` object overrides that
background; omitting it inherits the common backdrop, including later changes.
In the typed model, an inherited `PanelStyle.backdrop` is `null`, not a copied
default. The JSON field is omitted for inheritance rather than written as `null`.

Blur is compositor background blur clipped to the surface's visible rounded
region. It blurs what is behind the background, not the panel, popup or dialog's
own icons, text or other content. Opacity likewise affects only the background.
Radius 0 turns blur off. The requested dp radius is converted using the host's
density and capped at 150 physical pixels.

System blur support is optional and can change while a surface is open. When it
is unavailable or disabled, the background retains the chosen opacity without
blur; the stored appearance does not change. Standard system blur capability
signals control the effect. MagicDesk neither forces blur on nor substitutes
captured screenshots. Availability changes update the presentation without
restarting Desktop or acquiring new services. Blur does
not add a platform, privilege, HOME or input prerequisite to Appearance.

## Panels And Components

`composition.panels` contains 1-4 panels with unique stable `id` values of 1-32
ASCII lowercase letters, digits, `_` or `-`. Each declares `edge` (`top`,
`bottom`, `left`, `right`), `style`, and 1-24 ordered `components`. A component
kind may occur only once across all panels, except `spacer`.

Panel style uses `length` (`fill`, `content`), `alignment` (`start`, `center`,
`end`), `maxLengthDp` (64-4096), `sideGapDp` and `edgeGapDp` (0-96),
`thicknessDp` (0 for automatic, otherwise 40-160), `paddingDp` (0-16),
`radiusDp` (0-32), optional `backdrop`, and `reserveSpace`. Automatic thickness
uses the native host's normal sizing. All lengths are density-independent and
constrained to the available viewport. Start/end alignment follows the panel's
long axis. Reserving panels contribute edge intervals; rectangular window
consumers conservatively avoid an edge band. Non-reserving panels overlay the
workspace. These reservations do not alter Android task-area ownership.

Components are `start`, `tasks`, `show_desktop`, `open_tasks`, `notifications`,
`keyboard_layout`, `phone_screen`, `quick_controls`, `battery`, `clock`, `spacer`.
They retain production action controllers and semantic automation identities.
Each accepts `widthDp` (0 for automatic, otherwise 32-240), `minViewportDp`
(0-4096), and `visibility` (`always`, `expanded`, `external`). The Start component
accepts `label` (at most 32 printable characters); Clock accepts `clock` (`time`,
`date`, `date_time`). Unavailable controls do not start their services.

`composition.start` declares ordered `sections` (`recent`, `apps`, `running`,
`tools`), `presentation` (`grid`, `list`), `tileWidthDp` (80-200), and `iconSizeDp`
(24-64). `apps` is required and sections cannot repeat. Both presentations retain
the shared application catalog, profile identity, bounded pages, search and
existing launch destinations.

## Resources

`resources.icons` maps semantic symbols to built-in symbols: `desktop`, `windows`,
`notifications`, `keyboard`, `controls`, `files`, `terminal`, `settings`, `search`,
`camera`, `video`. These are single-step substitutions, not recursive aliases or
Android resource IDs.

A theme ZIP contains `theme.json` and optional raster assets under `icons/` and
`wallpapers/`, plus `.ttf` or `.otf` fonts under `fonts/`. `resources.iconAssets`
maps semantic symbols to bundle-relative image paths; `resources.font` and
`resources.wallpaper` select a font and wallpaper. Supported images are static
PNG, JPEG and WebP. The portable document omits `resources.bundle` or leaves it
empty. Import attaches the verified content digest; a JSON-only document with
assets therefore references an already-installed bundle.

```json
{
  "version": 4,
  "resources": {
    "iconAssets": { "files": "icons/files.png" },
    "font": "fonts/interface.ttf",
    "wallpaper": "wallpapers/workspace.jpg"
  }
}
```

ZIP import validates schema and all referenced asset kinds before atomic
publication. It rejects traversal, ambiguous paths, duplicate entries, invalid
media, scripts and non-allowlisted formats. Limits include 32 MiB archive size,
64 MiB expanded size, 256 entries, 16 MiB per entry, 4 MiB per font, 8192-pixel
image dimensions and 24 million aggregate decoded pixels. Installed storage is
bounded to 16 bundles and 256 MiB. Imported resources are immutable and
content-addressed; validation never follows external paths or fetches URLs.
**Remove unused theme bundles** explicitly reclaims unused storage, retaining
global/workspace current, committed, preview and staged resources. Cleanup and
appearance publication are coordinated by the shared store.

ZIP export captures the effective edited document and its verified resource bundle,
removing the installed digest from portable `theme.json`. It does not export an
outdated original document. JSON export captures the global document, or the
selected workspace's sparse patch; it carries no binary assets.

## Feedback And Motion

`feedback` maps `normal`, `hover`, `pressed`, `selected`, `focused`, `disabled`
and `outline` to palette roles, including `transparent`. Native controls retain
geometry as their state changes; defaults have no permanent idle backplate.

`motion.panels` and `motion.taskbar` accept `none` or `fade`. `durationMs` is
0-400, `feedbackMs` is 0-250, and `curve` is `linear`, `ease_out` or `smooth`.
`reduced` disables effects, as does Android's disabled animator setting. Effects
change presentation only; they never delay focus or submit task transactions.

## Automation

All scoped operations accept optional `workspaceKey`. Omit it for global defaults;
confirm/cancel must use the same scope as the exact returned `previewId`.

- `appearance.schema`: schema; observation.
- `appearance.validate`: resolve `document` without mutation; observation.
- `appearance.get`: effective and committed documents, sparse patches, known
  override keys, revision and preview ID; observation.
- `appearance.apply`: replace `document` or selected patch; control.
- `appearance.preview`: temporary `document` or patch; control.
- `appearance.confirm` / `appearance.cancel`: commit/discard exact preview; control.
- `appearance.preset`: apply `name` while retaining geometry/resources/motion; control.
- `appearance.reset`: reset global appearance or remove selected override; control.
- `appearance.import`: `path`, optional `format` (`zip` default, `json`), returning
  an unconfirmed preview; requires **control and files_read**.
- `appearance.export`: existing `directory`, optional `name` and `format`,
  returning an actual new file `path`; requires **files_write**. Existing files
  are not overwritten. Use `files.download_begin` on the returned path.
- `appearance.prune`: explicitly remove unused app-private bundles; control.
  Returns removed digests and count, without changing external files.

MCP file operations reuse verified shared Files descriptors and require file
service availability; SAF operations use the user's document grant. Neither path
provisions Desktop. One preview may be active per scope. Stale IDs cannot revert
later changes. A changed revision rejects a delayed import rather than silently
replacing a newer edit. Results identify accepted configuration, not completed
pixel presentation or durable disk I/O.

Examples: [multiple panels](themes/multi-panel.json), [compact dock](themes/compact-dock.json),
[light workspace](themes/light-workspace.json), [quiet controls](themes/quiet-controls.json).
