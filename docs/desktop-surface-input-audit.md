# Desktop-surface and Files input audit (UX-012f)

Source-only audit for [issue #42](https://github.com/aagocs/magicdesk/issues/42),
against main plus the open shortcut PR. Findings are **source findings**;
proposals are **proposed**; nothing here is device-verified. Direction: file
interaction on a workstation target follows familiar desktop file-manager
conventions ([product direction](product-direction.md)). Device data stays
private.

## What exists today

Shared keyboard mapping: `FileKeyboardCommand` (used by both the Files tool and
the desktop surface). Files handles the full set in `FileManagerActivity`;
the desktop surface handles a subset in
`DesktopWorkspaceController.handleKeyboardCommand`, reached from
`DesktopInputController.handleKeyEvent` when the shell has focus and no panel is
visible.

| Interaction | Files tool | Desktop surface |
| --- | --- | --- |
| Click selects; Ctrl+click toggles; Shift+click selects range | Present (`onItemClick`, `selectRange`, anchor path) | Single selection only (`selectedFile()`) |
| Double-click opens (single-click opt-in `openFilesWithSingleClick`) | Present (`ItemActivationPolicy` using the system double-tap timeout) | Not traced |
| Enter opens, F2 renames, Delete deletes, F5 refreshes, Esc clears selection | Present | Present (Open, Rename, Delete, Refresh, Clear) |
| Ctrl+C / X / V | Present | Present (single file) |
| Ctrl+A select all | Present | Mapped by `FileKeyboardCommand` but not handled |
| Ctrl+F find, Ctrl+L location, Ctrl+H hidden, Ctrl+N new window, Ctrl+Shift+N new folder | Present | Not handled |
| Backspace and Alt+Up go to the parent folder | Present (`UP`) | n/a |
| Right-click, Menu key and long-press context menu | Present (background and item menus) | Present (`DesktopContextMenuController`, Menu key at screen centre) |
| Drag and drop | Present (`startDragAndDrop`, list drop target) | Present (`DesktopWorkspaceController` drag listeners) |
| Delete semantics | Confirm dialog, then permanent delete (`OPERATION_DELETE`) | Confirm dialog |

## Gaps against a conventional desktop file manager

| # | Convention | Today | Class | Proposal | Confirming check |
| --- | --- | --- | --- | --- | --- |
| H1 | Multi-selection on the desktop surface: Ctrl/Shift+click, Ctrl+A | Single selection; `SELECT_ALL` falls to `default: return false` | Missing | Extend the desktop selection model to a set; reuse Files' anchor/range logic if it can be shared without copying; handle `SELECT_ALL`, multi-file copy/cut/delete | Select several desktop items with keyboard and mouse; copy/paste moves all |
| H2 | Rubber-band (drag-rectangle) selection | No rubber-band or marquee handler found in either view | Missing | Add as a view-level gesture on empty background; must not steal the background context-menu path or drag-and-drop | Drag on empty space selects intersecting items; right-click on empty space still opens the menu |
| H3 | Arrow-key movement of the selection | No handler in `FileKeyboardCommand` or the activities; whether Android focus traversal already moves selection was **not** traced | Unverified | Reproduce on a device first; if absent, add Up/Down/Left/Right/Home/End selection moves with Shift extending | Arrow keys move the highlighted item; Enter opens it |
| H4 | Shift+Delete deletes permanently; Delete moves to a recycle bin | Delete is always permanent after a confirm; no trash exists in the app; Shift+Delete is unmapped (`hasNoModifiers`) | Design decision | Decide whether a trash is wanted at all. If not, keep the confirm and map Shift+Delete to the same action without being a separate behavior | Decision recorded in the issue |
| H5 | Alt+Left / Alt+Right folder history | Only parent navigation (Backspace, Alt+Up) | Missing, low | Optional back/forward stack in the Files tool | History moves between visited folders |
| H6 | Middle-click opens in a new window or tab | No tertiary-button handling in the file views | Missing, low | Optional: open the folder in a new Files window | Middle-click on a folder opens a second window |
| H7 | Desktop surface shortcuts match Files | Ctrl+F, Ctrl+L, Ctrl+H and Ctrl+Shift+N are Files-only | Partial | Decide which make sense on a desktop with no location bar: at least Ctrl+Shift+N (new folder) | Chord creates a folder on the desktop |

## Notes

- Android-owned behaviors (system drag shadows, IME text selection in text
  fields) are not candidates; the Files key handler already stands aside when a
  text field has focus (`getCurrentFocus() instanceof EditText`).
- `openFilesWithSingleClick` is an existing user setting; its default was not
  recorded here, and the proposals do not change it.

## Follow-ups

Opened as separate issues with tier/env labels. H3 needs a device reproduction
before any implementation; H4 is a product decision, not a code task.
