# Workstation defaults audit (UX-022)

Source-only audit for [issue #13](https://github.com/aagocs/magicdesk/issues/13),
against main `887882e`. Findings below are **source findings**; proposals are
**proposed**, not implemented; nothing here is device-verified. Direction comes
from [product direction](product-direction.md) ("Simplify workstation defaults":
fewer MagicDesk-specific decisions, density, keyboard navigation, predictable
placement). Tracking: [UX-022](ux-work-plan.md). Device data stays private;
confirming checks below are described generically.

**Rule for every proposal:** a changed default applies only when the user has
never made the corresponding choice. It must not rewrite saved appearance,
window geometry, per-app scale or pins, and must not touch display 0 (phone)
behavior or the HOME/session lifecycle owned by UX-010.

## Defaults inventory

| # | Area | Current default (source) | Assessment | Proposal | Confirming device check |
| --- | --- | --- | --- | --- | --- |
| D1 | External display density | `DisplayDensityPolicy.recommendedExternalDpi`: DPI = short side × 160 / 1080, rounded to 4, clamped to the display's stable maximum; fallback 192 when the mode is unknown. Display 0 keeps system density (`DisplayProfileController.initialDpi`). | Sound: constant ~1080 dp short side, so a fresh 1080p/1440p/2160p monitor gives the same layout in dp; phone untouched. | Keep. | Fresh external display at two resolutions reports the formula's DPI and unchanged phone density. |
| D2 | Density constants disagree | `DesktopPreferences.DEFAULT_DESKTOP_DPI = 192` has no references in `main` or `test`. The policy fallback is also 192, while `VirtualDisplaySpec.DEFAULT_DPI` is 160 (1920×1080). | Dead constant plus a fallback that matches neither the formula at 1080p (160) nor the virtual-display default. | Delete the unused constant; make the unknown-mode fallback the 1080p value (160) unless a reason for 192 is found. Light-tier cleanup with a unit test on `DisplayDensityPolicy`. | None for the deletion; fallback needs a display whose mode is unreadable (rare), so rely on the unit test. |
| D3 | Virtual display creation | 1920×1080 @ 160 dpi (`VirtualDisplaySpec`, saved by `VirtualDisplayPreferences`). | Consistent with D1 at 1080p. Last-used values persist, so fresh and returning users differ by design. | Keep. | Create display with defaults; confirm size/density match D1 for 1920×1080. |
| D4 | Per-app scale | No profile ⇒ `DesktopTaskDensity.INHERIT` (display density). A profile scales display density by a percentage. | Correct: opt-in only. | Keep; do not introduce a non-100% default scale. | Launch an app with no profile; its density equals the display's. |
| D5 | Panel layout | `ShellComposition.defaults()` = one full-width bottom panel, auto thickness, every component kind except spacer in enum order: Start, tasks, show desktop, open tasks, notifications, keyboard layout, phone screen, quick controls, battery, clock. | Close to a conventional taskbar + tray. `phone_screen` and `battery` describe the phone, not the workstation. The component model already supports `visibility: EXTERNAL`. | Evaluate per-component default visibility on external displays (hide phone-oriented items by default, discoverable in Panel components). Changing it for fresh state only needs a "never saved" signal: `AppearanceStore` reads a missing `document` preference as `{}`, so it can be detected before the first save. | Fresh install on an external display: panel readable at 1080p-class width, nothing clipped, phone items reachable from Panel components. |
| D6 | Start presentation | `Start.defaults()`: sections Recent, Apps, Running, Tools; **GRID**, 100 dp tiles, 44 dp icons. The bundled Workbench theme uses a compact list. | Grid favors touch. Keyboard-first search with Up/Down/Page navigation (UX-020/023) maps naturally to a list. | Propose list as the fresh-state default on external workstation scopes only; keep grid for phone/touch scopes. Needs a decision on whether Start presentation can vary by scope without a new theme. | Fresh external Start shows list; phone Start unchanged; keyboard navigation per UX-020 checks. |
| D7 | Pinned apps | Fresh `taskbarApps` is empty; the taskbar shows only running tasks. | A Windows/Linux user expects a few launchers. Seeding must not repeat after a user unpins everything, but `DesktopStateStore` stores an empty list the same as a missing one, so "never saved" is not representable today. | Needs a state-format decision (explicit seeded marker) before any seeding; candidates would be built-in Files, Terminal/Console and Settings only. Defer; open a design issue if wanted. | Fresh install, then unpin all and restart: pins must stay empty. |
| D8 | Start launch destination and mode | Display selector defaults to the screen containing Start ("Current"); mode **Default** (`DesktopLaunchMode.AUTO`); "New window" unchecked ⇒ reuse existing task. Outside Desktop the result is an ordinary fullscreen Activity. | Matches the product direction (no implicit Desktop/HOME/input). The controls (destination ~52 dp row, mode button, 90 dp checkbox at 12 sp) sit in the main Start surface; dense but discoverable. | Keep behavior. Possible polish only after UX-020/023 device coverage: none proposed now. | Existing Start device scripts cover destination/mode; managed Desktop popup is #11. |
| D9 | New windows fullscreen on phone | Off; setting `phoneFullscreenByDefault`. | Phone-Desktop only, explicit choice. | Keep. | n/a |
| D10 | Appearance | `ShellAppearance.defaults()` = `dark` preset, backdrop from the default panel style. Four bundled themes ship (Workbench, Glass Dock, Two Panels, Contours). | Theme is a taste decision; the default is fine. Light/dark choice should not be forced. | Keep dark; consider mentioning theme choice in first-run guidance rather than changing it. | n/a |
| D11 | Discovery of advanced controls | `docs/getting-started.md` documents Settings > Desktop/Session/Appearance; session power options (screen on, wake lock, adaptive brightness) default off. | Off-by-default is correct for the phone; UX-011 covers the panel-off/portable output interaction separately. | No change here. | See UX-011. |
| D12 | Keyboard access | Hardware keyboard layout is a panel component; Start search is keyboard-driven (UX-020/023). Alt+Tab etc. owned by the shortcut state machine. | Audit of shortcut defaults belongs to UX-012. | None here. | UX-012 matrix. |

## Summary of actionable items

1. **D2 (light, host):** remove `DEFAULT_DESKTOP_DPI`, align the unknown-mode
   fallback with the 1080p value, add a `DisplayDensityPolicy` unit test.
2. **D5/D6 (standard, needs design + device):** fresh-state-only defaults for
   external scopes; requires a "never saved" signal in `AppearanceStore` and a
   decision on scope-dependent Start presentation.
3. **D7 (standard, design first):** pin seeding needs an explicit marker in the
   desktop state format; do not implement without that decision.

Everything else is retained as-is. No default in this audit changes HOME,
session ownership, display power or input routing.
