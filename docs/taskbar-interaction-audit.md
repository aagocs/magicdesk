# Taskbar and window interaction audit

Plan ID: UX-021; [audit issue #12](https://github.com/aagocs/magicdesk/issues/12).
Source baseline: main `8ede2817`. This is a **source-only audit**. No managed
Desktop, physical input or window transition was exercised for these findings.
Public summaries deliberately exclude personal environment and device data.

Read [product direction](product-direction.md), [fullscreen transitions](fullscreen-transitions.md)
and the [UX acceptance matrix](ux-work-plan.md#acceptance-matrix) before runtime
changes. Capture a current-main device reproduction at the owning boundary
before implementing a fix. Reuse [focused verification](testing-workflow.md)
between edits and run the full gate for the final runtime candidate.

## Existing behavior and owning boundaries

| Interaction | Source fact | Device reproduction to retain |
| --- | --- | --- |
| Click a running task | `TaskbarController.activate` passes its exact task to `AppTaskController.toggleTaskbarTask`. `DesktopTaskController.toggleTaskbarTask` resolves live ownership/focus, then activates or demotes through the existing gateway. | With two windows, click an inactive entry, then the active entry, then restore it. Record exact task identity, z-order, bounds/mode and retained application state. Repeat with a panel open. |
| Conceal / Show Desktop | `TaskbarTaskOrder` builds z-orders and restore sets around the Desktop host. Concealment is a desktop presentation state; it does not require Android application recreation or a window-mode change. | Conceal one task, invoke Show Desktop, restore the visible set. The already-concealed task stays concealed; freeform and fullscreen clients retain state. Repeat with a task closing while hidden. |
| Pinned versus running | `collectTaskbarItems` matches pinned `AppReference` identities to live tasks and renders one entry per matching task. Idle pins launch through the default path; live entries toggle the captured exact task. Running/active/attention indicators are painted in `createPin`. | Pin an app, open two windows, close one, close the last. Observe idle/running transitions, active/attention state, and overflow count. Repeat across profiles without publishing their identities. |
| Right-click / long press | `DesktopContextMenuController.registerTarget` installs native context-click and long-click handling; secondary pointer routing uses the registered target. `DesktopMenuNavigator` handles menu arrows, Tab, Enter and Escape. | Right-click the exact running entry, idle pin and overflow row. Navigate Window/Arrange using the keyboard, cancel, and verify no unintended application activation. Compare physical and captured pointer input. |
| Maximize / restore / snap | `DesktopWindowTransitionController` owns semantic arrangements, fullscreen restore and pending snap requests. `NativeWindowBoundsController.getSnappedBounds` derives halves/quarters from taskbar-aware work area and retains restore bounds. | Start from non-default window bounds; maximize, restore, snap half/quarter, restore. Repeat from fullscreen and with a non-bottom panel. Confirm mode, geometry, task identity and content placement. |
| Fullscreen | The existing typed transition gateway and retained fullscreen plane own mode/ordering. Activation alone must not resize, reparent or change mode. | Alternate freeform/fullscreen clients via taskbar and Alt+Tab; enter/leave fullscreen through native caption and shell action separately. Check stable task/Activity identity and no geometry changes on activation. |
| Android / terminal / X11 / Wayland peers | Built-in presentation can supply per-window title/icon/attention through `BuiltInWindowRegistry.present`; taskbar control still uses the Android host task identity. Hosted-client lifetime differs from whole-desktop viewer lifetime. | Repeat the above with Android, terminal, X11 and Wayland windows together. Distinguish closing one client, closing a viewer and concealing a task; verify PTY/session retention according to the existing lifecycle contract. |

Source owners:

- [TaskbarController](../app/src/main/java/io/github/mekhontsev/magicdesk/TaskbarController.java):
  `collectTaskbarItems`, `createPin`, `activate`, `renderPins`.
- [TaskbarOverflowController](../app/src/main/java/io/github/mekhontsev/magicdesk/TaskbarOverflowController.java):
  `setItems`, `createRow`; an open menu keeps its captured entries until dismissed.
- [AppTaskController](../app/src/main/java/io/github/mekhontsev/magicdesk/AppTaskController.java):
  `focusTask`, `toggleTaskbarTask`; panel snapshots do not authorize replaying task order.
- [DesktopTaskController](../app/src/main/java/io/github/mekhontsev/magicdesk/DesktopTaskController.java)
  and [TaskbarTaskOrder](../app/src/main/java/io/github/mekhontsev/magicdesk/TaskbarTaskOrder.java):
  live activation/concealment and Show Desktop restore ownership.
- [DesktopContextMenuController](../app/src/main/java/io/github/mekhontsev/magicdesk/DesktopContextMenuController.java)
  and [DesktopMenuNavigator](../app/src/main/java/io/github/mekhontsev/magicdesk/DesktopMenuNavigator.java):
  context targets, action availability and keyboard navigation.
- [DesktopWindowTransitionController](../app/src/main/java/io/github/mekhontsev/magicdesk/DesktopWindowTransitionController.java)
  and [NativeWindowBoundsController](../app/src/main/java/io/github/mekhontsev/magicdesk/NativeWindowBoundsController.java):
  maximize/restore, taskbar-aware snapping and fullscreen changes.
- [BuiltInWindowRegistry](../app/src/main/java/io/github/mekhontsev/magicdesk/BuiltInWindowRegistry.java):
  built-in/hosted window presentation and identity.

## UX-021a: context-menu pin-state identity

**Source finding; visible bug not yet device-verified.**
[Follow-up #18](https://github.com/aagocs/magicdesk/issues/18).

`DesktopContextMenuController.showAppMenu` checks
`getPinnedApps().contains(state.app.packageName)`. The list contains
`AppReference` values, while `packageName` is a String. The pin action in
`TaskbarController.togglePinned` correctly adds/removes `app.reference`.
`AppReference.equals` compares profile-scoped application, built-in entry and
hosted recipe; a package string is not that identity. Menu presentation and
the actual action therefore consult different identities.

Exact reproduction: pin a generic app, reopen its taskbar context menu, inspect
Pin versus Unpin, invoke the action and reopen. Repeat for the same package in
two profiles and distinct pinnable built-in/hosted entries where available.
Check both label and persisted pin state after each toggle. Keep these identities
private; report aggregate results.

Fix boundary: menu presentation only. Use the same `AppReference` membership as
the existing action and prove profile distinction with a focused production-body
fixture. Do not simplify identity to package names or change launch/focus policy.

## UX-021b: taskbar keyboard interaction during refresh

**Source finding; focus loss not yet device-verified.**
[Reproduction issue #19](https://github.com/aagocs/magicdesk/issues/19).

`DesktopTaskSnapshotController.sync` calls `renderTaskbarPins` for new snapshots.
`TaskbarController.renderPins` removes every button and creates new views. That
path does not capture/restore the previously keyboard-focused taskbar entry or
its visible viewport. This establishes view replacement; actual Android focus
behavior needs a device reproduction.

Exact reproduction: keyboard-focus one taskbar entry; open/close another test
window or change a built-in window presentation to trigger a snapshot. Record
focused control and visible viewport before/after, then press Enter and observe
the exact activated task. Repeat with overflow, deletion of the focused task,
and idle-pin/running-task transitions. Distinguish physical/captured keyboard
input from semantic task activation.

If loss is reproduced, retain UI interaction by stable task/application identity
and define a fallback for removal. UI focus retention is not permission to focus
another Android task or replay the application stack. Keep the existing observer
and task-control gateway. If no loss occurs, document the observed mechanism
before making a change.

## Validation and handoff

The audit changes documentation only; file/link, source-contract and whitespace
checks apply. No Android build or device mutation is needed for this artifact.
Runtime follow-ups must meet their own baseline, fixture, full CI and device gates.
Wired and portable sessions remain equally important; one does not establish the
other. Independent Start coverage is not managed taskbar coverage.

Issue #12 owns this audit. Issues #18/#19 own follow-ups. The HOME/session
membership work in #6 has a separate owner and branch; this audit changes no
runtime source or ownership model. Use [coordination issue #4](https://github.com/aagocs/magicdesk/issues/4)
for claims and handoffs, and Discussions for cross-cutting proposals. Keep
implementation and verification status in the linked issues rather than
duplicating another agent's changing claim in this document.
