# Fork UX implementation plan

Read [product direction](product-direction.md) for the target experience and
[AGENTS.md](../AGENTS.md) for engineering constraints. This document is the
implementation backlog and agent handoff for that direction. Architecture
guides describe current behavior; work items here describe proposed changes.

**Start here:** establish UX-001's device baseline, then investigate UX-010's
HOME hosting boundary. UX-020 is the first bounded desktop shell candidate;
it can progress independently once its reproduction is established. Do not
attempt the entire UX redesign in one change.

## Current status

- Source audit completed against `ec4bd633` on 2026-10-02. No runtime UX changes
  have been implemented by this plan.
- The owner selected **both equally**: wired external monitor with mouse and
  keyboard, and portable/virtual Desktop viewed and controlled from a PC through
  scrcpy. Neither is a secondary target. Wireless remains a supported regression
  target; explicit phone Desktop must remain functional.
- GitHub Issues were disabled when this plan was created. Track stable IDs here
  until issue tracking is enabled, then link issues rather than duplicating their
  changing status. Do not enable repository settings as part of UX work.
- Device baseline, reproduction, build and runtime validation are pending.
  The kickoff environment had no discovered MagicDesk MCP tools, no Java/ADB on
  PATH and no Android SDK at the usual local Windows location. These observations
  are local limitations, not project build failures or device incompatibilities.

Statuses mean: **investigated** = source boundary traced; **planned** = acceptance
defined; **in progress** = implementation underway; **verified** = required checks
and current device evidence recorded. A source finding alone is not a verified
user-visible bug. Update status and evidence with each implementation change.

## Work queue

| ID | Priority | Outcome | Status | Prerequisite |
| --- | --- | --- | --- | --- |
| UX-001 | P0 | Reproducible wired and portable baseline | Planned | Authorized device and build/tool access |
| UX-010 | P0 | External-only Desktop preserves OEM HOME | Investigated; design unproven | UX-001 and framework hosting probe |
| UX-011 | P0 | Portable output remains live with phone panel off | Planned | UX-001; repeat after UX-010 |
| UX-012 | P0 | Direct PC and physical mouse/keyboard control | Investigated; device audit pending | UX-001; repeat after UX-010 |
| UX-020 | P1 | Start keyboard selection stays visible and stable | Investigated; reproduction pending | UX-001's relevant Start baseline |
| UX-021 | P1 | Consistent taskbar/window interactions | Planned | UX-001 and focused interaction audit |
| UX-022 | P1 | Useful, compact workstation defaults | Planned | UX-020/021 findings and fresh-settings baseline |

P0 items define the product contract. A small P1 shell improvement may ship
independently, but it does not fulfill the OEM-phone goal. Do not mark a P0 item
complete because a visual theme looks more like Windows or Linux.

## UX-001: establish the baseline

Read the baseline workflow in [AI-assisted device
support](ai-assisted-device-porting.md#establish-a-baseline),
[compatibility](compatibility.md) and [automation](automation.md). Verify actual
tool availability rather than assuming upstream maintainer setup exists here.

Record the exact source/build identity, Android version/fingerprint, privilege
UID, active framework/platform adapters, OEM launcher package/component and
navigation mode. Identify the workspace display and, for portable sessions, its
viewer/capture target separately. Record scrcpy version and actual options, input
transport, pointer capture/release method and whether phone power was changed
through Android, MagicDesk or scrcpy. Those paths are not interchangeable.

Run the acceptance matrix on unmodified current `main` first, preserving the
complete refreshed compatibility report and exact reproduction. Record known
failures and unavailable paths without weakening existing self-test assertions.
Private reports/captures stay outside tracked source; add a sanitized summary
and the evidence reference to the relevant work item.

## UX-010: external workspace ownership without phone HOME takeover

The traced startup chain is
[DesktopSessionController.show](../app/src/main/java/io/github/mekhontsev/magicdesk/DesktopSessionController.java)
-> [DesktopHomeRoleLease.prepare/activate](../app/src/main/java/io/github/mekhontsev/magicdesk/DesktopHomeRoleLease.java)
-> external HOME host launch. The lease stores one previous primary and secondary
HOME selection with membership for all workspaces. Session lookup also requires
an active matching HOME lease; this coupling exists beyond the role-setting call.

[DesktopHomeSurfaceRouter.Selection.surfaceOn](../app/src/main/java/io/github/mekhontsev/magicdesk/DesktopHomeSurfaceRouter.java)
returns the ordinary MagicDesk launcher on display 0 whenever any external
workspace exists and no phone workspace exists.
[ShellDesktopHostLauncher](../app/src/main/java/io/github/mekhontsev/magicdesk/ShellDesktopHostLauncher.java)
explicitly requests and verifies `ACTIVITY_TYPE_HOME` on the target display.
These source facts explain why simply omitting primary HOME activation is not
yet an implementation: host admission and framework behavior need evidence.

Investigate whether the existing typed launch boundary can host an external
workspace as secondary HOME while the user's primary HOME remains unchanged.
Probe this through existing framework adapters under shell UID 2000, on supported
Android versions, without a second organizer or another privileged identity.
The current explicit HOME launch is an investigation entry point, not proof that
Android will admit it with the OEM launcher retained.

Before implementation, write a short decision here with observed launch result,
actual host display/type, task ordering, OEM Home/Recents behavior and failure
cleanup. If the framework cannot provide the desired host, document its exact
constraint and evaluate a bounded alternative within the current task ownership
model. Do not replace the desktop host with an ordinary fullscreen Activity
without demonstrating wallpaper, stacking and fullscreen-plane behavior.

The proposed ownership separation must cover these transitions:

| Transition | Required primary-HOME behavior |
| --- | --- |
| No workspace -> external-only workspace | Preserve OEM selection and phone foreground |
| External workspace -> another external workspace | Preserve OEM selection; isolate workspace ownership |
| External-only -> explicit phone Desktop | Acquire phone HOME deliberately; external workspace continues |
| Phone + external -> external-only | Release phone HOME while external workspace continues |
| External-only -> no workspace | Preserve OEM selection; retain independent displays/tools |
| Failed start, display loss or process recovery | Restore only owned state; honor a user's newer launcher selection |

Session membership must remain independently verifiable when primary HOME is
unowned. Trace close/recovery through
[DesktopSessionTransitionCoordinator](../app/src/main/java/io/github/mekhontsev/magicdesk/DesktopSessionTransitionCoordinator.java)
and [DesktopHomeStartupGuard](../app/src/main/java/io/github/mekhontsev/magicdesk/DesktopHomeStartupGuard.java).
Review phone-control-panel presentation and Recents routing too: preserving the
launcher setting is insufficient if cleanup or routing still interrupts OEM use.

Extend the existing `DesktopHomeRoleLeaseTest`,
`DesktopHomeStartupGuardTest` and `DesktopSessionTransitionCoordinatorTest` at
their owning boundaries. Required device checks include phone, simulated and
wired self-tests plus portable capture, mixed phone/external residency, failed
start and process recovery. Existing fullscreen/task-area guardrails continue
to apply. Acceptance is the product-direction P0 HOME criteria **and** these
mixed-residency transitions, not merely successful external host creation.

## UX-011: physical phone power versus portable output

Start with [ShellVirtualDisplays](../app/src/main/java/io/github/mekhontsev/magicdesk/ShellVirtualDisplays.java),
[DisplayPresentations](../app/src/main/java/io/github/mekhontsev/magicdesk/DisplayPresentations.java),
[DesktopScreenPolicy](../app/src/main/java/io/github/mekhontsev/magicdesk/DesktopScreenPolicy.java),
[PlatformPhoneUiDriver](../app/src/main/java/io/github/mekhontsev/magicdesk/PlatformPhoneUiDriver.java)
and the close coordinator. Classify physical panel power, virtual-display state,
frame production, viewer/capture attachment and keyguard as separate observations.
Do not attribute black output to a display driver until the failed boundary is
known. Repeat this matrix after changing HOME: firmware power behavior may depend
on HOME ownership, as documented in [architecture](architecture.md).

Acceptance: an unlocked phone's panel-off operation does not end or freeze an
otherwise permitted portable workspace; output resumes correctly after viewer
detach/reattach and monitor changes; Close Desktop retains its independent
display; explicit display removal releases it. Lock/unlock must follow actual
Android security policy. Document any protected-output limitation rather than
bypassing it or treating an expected keyguard restriction as a liveness bug.

## UX-012: direct desktop input

The current shared owners are
[DisplayInputSession](../app/src/main/java/io/github/mekhontsev/magicdesk/DisplayInputSession.java),
[RuntimeDisplayInputCoordinator](../app/src/main/java/io/github/mekhontsev/magicdesk/RuntimeDisplayInputCoordinator.java)
and [RuntimeInputCoordinator](../app/src/main/java/io/github/mekhontsev/magicdesk/RuntimeInputCoordinator.java).
Shortcut interpretation belongs to
[KeyboardShortcutStateMachine](../app/src/main/java/io/github/mekhontsev/magicdesk/KeyboardShortcutStateMachine.java);
window commands retain the existing semantic task gateway. Physical input does
not pass through the phone touchpad UI. Do not introduce a new route merely
because PC input needs a clearer default or capture affordance.

For wired USB/Bluetooth and portable PC input, reproduce direct pointer movement,
left/right/middle click, wheel, drag/resize, capture/release, text and modifiers,
Alt+Tab and Shift+Alt+Tab. Test display switching and disconnect during held keys
or buttons for stuck input. Check the phone remains touch-usable while the desktop
owns physical/remote input. App launch destination must not implicitly acquire
input. Extend existing input lifecycle/routing and shortcut fixtures only at
the boundary identified by the reproduction.

## UX-020: first bounded shell candidate — Start keyboard navigation

Source finding: in
[StartMenuContent](../app/src/main/java/io/github/mekhontsev/magicdesk/StartMenuContent.java),
`handleSearchKey` changes an integer selection and calls `renderBody`, which
removes every body child. `renderSearchResults` then creates a new result list
and `ScrollView` without explicitly revealing the selected row. Asynchronous
search updates also rebuild the body. Selection is an index rather than a stable
entry identity. These are code-level observations; on-device behavior remains
unverified.

Reproduce with enough matching apps/files to exceed the visible search viewport:
open Start, type a query, repeatedly press Down beyond the first visible page,
then Up and Enter. Repeat while file results arrive, while using the mouse wheel,
and after editing the query or changing destination. Compare the selected label,
visible highlight, scroll position and actual launched entry.

Proposed behavior:

- Keep the selected row visible after keyboard navigation without losing search
  focus, caret position or bringing up the software keyboard.
- Keep the result viewport stable for selection-only changes; asynchronous
  updates preserve selected entry identity while it is still present.
- Define a deterministic fallback when that entry disappears; a changed query
  or destination resets selection deliberately.
- Enter launches the selected entry once through the existing host;
  Escape dismisses Start. Mouse click, wheel, context menus, phone Start and
  accessibility remain usable.

Prefer a contained result-list/selection change over replacing all of Start.
Preserve [StartSearchController](../app/src/main/java/io/github/mekhontsev/magicdesk/StartSearchController.java)'s
search lifecycle and [StartEntryLauncher](../app/src/main/java/io/github/mekhontsev/magicdesk/StartEntryLauncher.java)'s
placement semantics. Existing `StartSearchControllerTest`, `StartDestinationTest`
and `StartEntryAppearanceTest` provide nearby coverage. Verify meaningful
selection/update cases and actual row visibility on a device; source assertions
alone cannot prove Android layout or keyboard behavior.

## UX-021/022: interaction audit and defaults

Audit taskbar activation, pinned versus running indicators, right-click menus,
minimize/conceal, maximize/restore, snap and fullscreen with Android, terminal,
X11 and Wayland windows together. Start at
[TaskbarController](../app/src/main/java/io/github/mekhontsev/magicdesk/TaskbarController.java)
and [DesktopTaskController](../app/src/main/java/io/github/mekhontsev/magicdesk/DesktopTaskController.java);
read [fullscreen transitions](fullscreen-transitions.md) before changing focus
or task order. Separate activation from intentional concealment. Do not recreate
Activities to switch tasks.

Record one before/after reproduction per proposed fix. Audit fresh settings
separately from existing user themes: target display choice, launch defaults,
scaling, panel density, keyboard access and discovery of advanced controls.
[StartLaunchControls](../app/src/main/java/io/github/mekhontsev/magicdesk/StartLaunchControls.java)
already has explicit destination/presentation controls; simplify their common
path only after identifying friction. Improve initial defaults without silently
overwriting a user's appearance or window preferences.

## Acceptance matrix

Every implementation records both **current-main baseline** and **changed-build
result**, plus build identity and evidence reference. Use pass/fail/unavailable;
unavailable is pending coverage. Select checks by changed boundary, following
[contribution verification](../CONTRIBUTING.md#verification) and `AGENTS.md`.

| Scenario | Observation required | Applies to |
| --- | --- | --- |
| Wired external session | OEM HOME component unchanged before/during/after; phone Home/Recents/notifications work; pointer/keyboard and task switching work | UX-010/012/021/022 |
| Portable PC/scrcpy session | Same OEM phone assertions; direct remote input, capture/release and exact workspace/viewer identity | UX-010/012/021/022 |
| Phone panel off while unlocked | Portable frames keep updating; wired output stays usable; panel restore succeeds | UX-010/011 |
| Phone lock/unlock | Actual secure/keyguard outcome recorded; permitted output resumes without lost ownership | UX-010/011 |
| Close, unplug, viewer detach, reconnect | Correct ownership cleanup; independent tools/displays retained; no stuck input or foreground phone takeover | UX-010/011/012 |
| Explicit phone Desktop plus external workspace | Starting/closing phone Desktop acquires/releases its HOME ownership without closing the external workspace | UX-010 |
| Failed start and process recovery | No stale MagicDesk HOME selection; user's newer launcher choice respected; owned resources reconciled | UX-010 |
| Start with more results than viewport | Arrow-selected row visible; stable identity through updates; Enter opens intended result; phone Start regression checked | UX-020 |
| API 34 independent tools | No Desktop preparation, HOME claim or new startup prerequisite | Any shared-runtime change |

The simulated self-test remains a required task/window regression check, but it
does not replace portable PC/scrcpy input/capture validation or wired hardware.
Run `verifyDevelopment` for implementation changes and the boundary-specific
device/self-tests; documentation-only changes need link, contract and diff checks.

For each completed item, add: implementation/PR reference, changed boundary,
baseline and changed-build evidence, automated checks, device targets, remaining
limitations and the next actionable item. Keep this handoff factual: proposed,
implemented and verified are separate states.
