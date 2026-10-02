# Desktop keyboard shortcuts audit (UX-012a)

Source-only audit for [issue #30](https://github.com/aagocs/magicdesk/issues/30),
against main `0fc0f69`. Findings are **source findings**; proposals are
**proposed**; nothing here is device-verified. Direction: mouse and keyboard on a
workstation target follow familiar Windows/Linux conventions
([product direction](product-direction.md)). Device data stays private.

## How shortcuts reach the code

- `DesktopShortcutService` (accessibility key filter) feeds each external
  keyboard's events to its own `KeyboardShortcutStateMachine.accept(...)` and
  passes any resulting action to `DesktopShortcutActions.dispatch`. Consumption
  is decided by the state machine, not by the service.
- Window chords end in `DesktopOperations.manageActiveWindow(SHORTCUT_*)`, i.e.
  the same semantic task gateway as the taskbar and MCP, as required by
  `AGENTS.md`. Alt+Tab and display switching use `advanceAltTab` and
  `DisplaySwitchController`.
- Outside the filter, `DesktopInputController.handleKeyEvent` handles a Meta
  tap and the Menu key when the MagicDesk shell window has focus.
- Outside Desktop (`desktop == false`) only Ctrl+Alt+Tab display switching is
  active; all other chords pass through.

## Current chords (source of truth: `KeyboardShortcutStateMachine.action`)

| Chord | Action | Notes |
| --- | --- | --- |
| Alt+Tab / Alt+Shift+Tab | Task switcher, committed on Alt release | Conventional |
| Ctrl+Alt+Tab (then Tab / Shift+Tab, Esc cancels) | Display switch | Not a persistent switcher as on Windows |
| Alt+F4 | Close active window | Conventional |
| Ctrl+Space | Toggle hardware keyboard layout | See G2 |
| Esc | Dismiss transient MagicDesk UI | Not consumed; reaches the app too |
| Win+Up | Fullscreen (`SHORTCUT_FULLSCREEN`) | MagicDesk's maximize equivalent |
| Win+Down | Restore, then minimize | Matches Windows |
| Win+Left / Win+Right | Snap half; then Up/Down while Win held walks corners | Windows-like, stateful |
| Win+D | Show/restore desktop | Conventional |
| Win+L | Lock device | Conventional |
| Win+I | MagicDesk settings | Windows opens Settings |
| Win+N | Notifications | Windows: notification center |
| Win+Q | Android system controls | Windows: search; see G3 |
| Win+Backspace | Android Back | No desktop analog |
| Win+Print Screen / Win+Shift+Print Screen | Screenshot / recording | |
| Win+/ | Shortcut help | |

Held keys (`repeats != 0`), and every Win+Shift combination other than Print
Screen, return `NONE` unconsumed and reach the focused application.

## Gaps against a conventional desktop set

| # | Convention | Today | Class | Proposal | Confirming check |
| --- | --- | --- | --- | --- | --- |
| G1 | Tapping the Windows key opens Start | A Meta press/release is consumed with `Action.NONE` by the state machine. Start is toggled only in `DesktopInputController` when the shell window has focus **and** `!isFullKeyboardShortcutMode()`. With the key filter ready, that branch is skipped, so no code path toggles Start on a lone Meta tap. | Possible missing behavior | Emit a Start action on Meta release when no chord key was used in between, from the state machine; keep suppressing the system's own Meta action. | Filter active, app focused: tap Win → Start opens once; Win+D etc. do not also open Start. |
| G2 | Layout switch is Win+Space (Windows, GNOME) or Alt+Shift | Ctrl+Space is consumed in every Desktop context, including hosted Linux apps and Android editors where it is completion/set-mark. | Conflicts with application chords | Move the default to Win+Space; keep Ctrl+Space only if the user opts in. | Terminal/editor receives Ctrl+Space; Win+Space switches layout. |
| G3 | Win+Q / Win+S = search, Win+A = quick settings | Win+Q opens system controls; Start search has no direct chord. | Convention mismatch | Win+S (and Ctrl+Esc) open Start with search focused; system controls to Win+A. | Chord opens the intended panel on the selected display. |
| G4 | Win+E opens the file manager | No chord; a built-in Files tool exists. | Missing | Win+E launches the built-in Files through the existing application-launch placement path. | Opens on the input display, reuses an existing window. |
| G5 | Ctrl+Shift+Esc opens the task manager | No chord; a built-in task manager exists (`TaskManagerActivity`). | Missing | Map to the built-in task manager entry. | Opens once on the input display; outside Desktop the chord still reaches the focused app. |
| G6 | Win+1…9 activates or launches the nth taskbar item | No chord. | Missing | Activate through `DesktopTaskController` focus path; launch pinned item when not running. Depends on pin identity (#18) and stable taskbar order. | Same entry as the nth taskbar button; no second observer. |
| G7 | Win+Shift+Left/Right moves the window to another display | `if (shift) return NONE` after Print Screen. | Missing | Move through the existing task-transfer path; display ordering must match Ctrl+Alt+Tab. | Window changes display, keeps mode and focus. |
| G8 | Win+Tab / overview | No chord in the state machine. `AGENTS.md` and product direction refer to an overview that shares the task gateway; its entry point was not traced here. | Missing | Trace the overview entry point, then map it. Low priority. | Chord opens the same overview as its current trigger. |
| G9 | Alt+Space window menu | No chord. | Missing | Optional; needs a window-menu target by exact task. Low priority. | |

Android-owned chords (Home, Recents, Back via system keys, power) remain
Android's and are not candidates.

## Follow-ups

Opened as separate issues with tier/env labels; each keeps ordinary typing
pass-through intact and extends `KeyboardShortcutStateMachineTest` before any
dispatch change.
