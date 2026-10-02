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

- Source audit completed against `ec4bd633` on 2026-10-02. UX-020 now has an
  implementation merged in [PR #2](https://github.com/aagocs/magicdesk/pull/2);
  full Linux/Windows CI and changed-build phone and independent virtual Start
  checks pass. Managed workstation coverage is pending. Page navigation is the
  next bounded increment; see the new evidence below.
- Wired monitor/mouse/keyboard and portable PC/scrcpy sessions have equal
  priority. Wireless remains a regression target; explicit phone Desktop must
  remain functional.
- GitHub Issues were disabled when this plan was created. Track stable IDs here
  until issue tracking is enabled, then link issues rather than duplicating their
  changing status. Do not enable repository settings as part of UX work.
- Complete current-main compatibility and exact Start reproductions were
  captured privately on API 35 under shell UID 2000. Changed-build independent
  Start checks pass on the phone and an owned 1280x720/160-dpi virtual display;
  actual scrcpy capture succeeds. These do not establish managed Desktop or
  physical keyboard/mouse coverage. Temporary resources were removed by exact
  identity; OEM HOME was retained.
- Runtime `442610ee` passed Linux/Windows CI and independent Start device checks.
  Build identity and environment details remain in private evidence.
- Local host fixtures are available; full Android/native verification uses CI.
  Wired hardware and portable physical-input coverage remain pending, with equal
  product priority. Never include personal setup details or credentials here.

Statuses mean: **investigated** = source boundary traced; **planned** = acceptance
defined; **in progress** = implementation underway; **verified** = required checks
and current device evidence recorded. A source finding alone is not a verified
user-visible bug. Update status and evidence with each implementation change.

## Work queue

| ID | Priority | Outcome | Status | Prerequisite |
| --- | --- | --- | --- | --- |
| UX-001 | P0 | Reproducible wired and portable baseline | In progress; phone and independent virtual Start baseline captured | Managed Desktop, wired and PC input coverage |
| UX-010 | P0 | External-only Desktop preserves OEM HOME | Investigated; [ownership design](https://github.com/aagocs/magicdesk/wiki/HOME-Ownership-Design) proposed; membership read boundary implemented, device probe pending | UX-001 and framework hosting probe |
| UX-011 | P0 | Portable output remains live with phone panel off | Planned | UX-001; repeat after UX-010 |
| UX-012 | P0 | Direct PC and physical mouse/keyboard control | Investigated; device audit pending | UX-001; repeat after UX-010 |
| UX-020 | P1 | Start keyboard selection stays visible and stable | Merged; CI, phone and independent virtual Start pass | Managed workstation and physical input coverage |
| UX-021 | P1 | Consistent taskbar/window interactions | Planned | UX-001 and focused interaction audit |
| UX-022 | P1 | Useful, compact workstation defaults | Planned | UX-020/021 findings and fresh-settings baseline |
| TEST-001 | P1 | Faster, repeatable development feedback | Implemented; focused/full CI and semantic device checks pass, including failure cleanup | Extend focused suites as owning boundaries change |
| UX-023 | P1 | Start Page Up/Down moves by the visible page | In review; host, Linux/Windows CI and phone/independent virtual Start pass | Managed workstation and physical input coverage |
| WS-030 | P1 | Daily workstation workflows | Planned; see [workstation roadmap](workstation-gaming.md) | OEM ownership, input and shell foundations |
| GAME-001 | P1 | Gaming compatibility preflight | Planned; Linux client path unverified | Explicit environment preparation; see [gaming stages](workstation-gaming.md#staged-work-and-acceptance) |

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

### Environment preflight and next agent

Discover authorized devices, current transports, MCP availability and runtime
identity each session. Do not assume a particular endpoint, device, permission
set or local toolchain. Reuse a healthy authorized service and existing
credentials; never restart healthy Shizuku merely to repeat setup. Keep all
connection tokens, hardware/network identifiers, account details and full
compatibility/UI reports outside tracked source.

Future test updates must use the existing development certificate to preserve
settings and authorization. Unsigned CI APKs need a verified signature. Never
uninstall or clear application data without explicit approval. Close an active
Desktop through production cleanup before installation. Manual CI produces
unsigned artifacts; main-branch publishing requires separately configured
release-signing secrets. Forking source does not supply upstream signing keys.
Verify signing configuration before promising an installable release.

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

[HOME ownership design](https://github.com/aagocs/magicdesk/wiki/HOME-Ownership-Design) records the traced lease structure,
framework hypotheses, proposed split and the device probe protocol (P0-P5).

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
entry identity. These observations have now been reproduced on the unchanged
phone build: independent Apps/Start on display 0, query `a`, focus search, then
14 Down key presses. The selected row is reported `selected=true`,
`visible=false`, outside the ScrollView's visible bounds, while the search
EditText remains focused. Independent Start on an owned 1280x720/160-dpi virtual
display reproduces the same invisible selection after eight Down presses.
scrcpy captures the actual virtual display. This establishes the shared Start
defect; managed Desktop UI and physical keyboard behavior remain unverified.

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

### Implementation in review

[StartSearchSelection](../app/src/main/java/io/github/mekhontsev/magicdesk/StartSearchSelection.java)
retains selection by stable entry key, clamps to the previous position when an
entry disappears, and resets on query/destination changes. Start updates the
existing row highlights for arrow navigation and launches the rendered selected
entry; held Enter repeats do not launch again. Its result ScrollView restores
viewport/reveals the selected row in `onLayout`, after Android positions children.
Result updates retain the viewport and a visible/pending keyboard selection,
without pulling a mouse-scrolled offscreen selection back into view.

Host validation: `StartSearchSelectionTest`, `StartSearchNavigationTest`,
`StartDestinationTest` and `ApplicationCatalogTest` pass together (23 tests),
including actual production
key-handler/layout-adapter bodies through the existing runtime fixture. The
pre-change host reproduction fails on viewport rebuilding and selection drift.
Full CI assembly/Lint and changed-build phone, wired and portable results must be
recorded before this work item is marked verified.

The first fork CI run stopped at an inherited version gate that required release
tags, before compilation. The gate now validates a tagless fork's first version
and still requires both name and code to exceed a tagged release. Seven isolated
Git-fixture tests cover both paths; no release tags or version numbers changed.
The next full run compiled Android code and ran 3,321 app tests; one existing
catalog-loading source guard failed after rendering was reordered. Loading now
remains before search rendering and that guard passes locally. The Linux job in
[CI run 36940961879](https://github.com/aagocs/magicdesk/actions/runs/36940961879)
passes at runtime commit `442610ee`: 3,576 tests across five modules, zero
failures/errors, two skipped app tests, release Lint/assembly, independent X11
host, native/boundary fixtures and APK checks. Windows `verifyDevelopment` and
the independent X11 host also pass in that run; its reports record 3,591 tests,
zero failures/errors and 48 skips. The manual CI run validates unsigned artifacts;
no release was published.

Changed-build independent Start checks on API 35 under shell UID 2000:

- Phone independent Start: query `a`, 14 Down presses beyond the first viewport,
  then three Up presses. The selected actionable row is fully inside the
  ScrollView, with positive geometry; search remains focused with query `a`.
- Independent virtual Start: same checks at 1280x720/160 dpi, eight Down presses
  then three Up presses. Actual scrcpy 4.1 capture succeeds on the changed build.
- Both surfaces: changing the query to `Shizuku` selects its matching row;
  Enter opens that application on the requested display. Escape closes a newly
  opened Start task, confirmed by fresh global `task_absent` observation.
- Cleanup: the exact owned virtual display is removed and `display_absent`
  confirms removal; the exact awake lease is released. Phone Home returns to
  the existing OEM launcher. No Desktop/HOME lease or display-input route was acquired.

Private evidence uses the `changed-phone`/`changed-virtual` UI snapshots, complete
compatibility report and changed virtual-display recording alongside the baseline
captures. Host fixtures cover stable identity across asynchronous insertions,
held Enter and mouse-scrolled viewport retention; those cases are not claimed as
physical device input coverage. Managed Desktop's Start popup, physical PC
keyboard/mouse and wired testing remain pending. This bounded shell fix does not
resolve UX-010's package-wide HOME lease.

## TEST-001 / UX-023: quick feedback and Start page navigation

[Fast testing](testing-workflow.md) documents the new JDK-only runner, focused CI,
semantic device script, cache contract and one-final-candidate verification flow.
The new paging fixture failed against the unchanged production handler, then
passed after implementation: 24 focused tests in 2.64 seconds. No Android/native
toolchain setup is needed for this feedback loop.

Current-main runtime baseline: merge `1a17ede1` contains runtime `442610ee`.
A complete refreshed compatibility report and exact phone/owned virtual Start
reproductions are retained privately. Query `a`, focused search, Page Down then
Page Up leaves selection unchanged on both surfaces. Escape closes the exact
task. No Desktop/HOME/input ownership is acquired. The semantic script confirms
that baseline and owned cleanup. Device identity, connection and report data
are deliberately excluded from public documentation.

Implemented candidate `94ca9f86`: `StartMenuContent` derives a page size from the
measured result viewport and row height, clamps it to at least one result and
moves selection through the existing selection/reveal path. Page keys retain
search focus and existing views; they are not consumed for empty results.
Home/End remain normal search text-editing keys. The focused fixture verifies
measured paging, end clamping, tiny/unlaid viewport fallback and no body rebuild.
[PR #3](https://github.com/aagocs/magicdesk/pull/3) contains this increment.
[Full CI run](https://github.com/aagocs/magicdesk/actions/runs/36944630655)
passes at this exact runtime candidate. Linux: 3,577 tests, zero failures/errors, two skips,
release Lint/assembly, native fixtures, independent X11 host and APK boundaries.
Windows `verifyDevelopment` and the independent X11 host pass: 3,592 tests,
zero failures/errors and 48 skips. Later commits change documentation and the
separately exercised device script; Android runtime/build inputs remain identical.
The automatically duplicated PR full build was canceled after that gate passed;
the separate quick host workflow validates the PR head without another native build.
Changed-build independent Start on phone and owned virtual display passes Page
Down/Up, fully visible selected-row geometry, retained search focus and Escape
closure with global task observation. Exact display and awake-lease cleanup pass.
The script also rejects an intentional wrong behavior expectation and cleans up
the owned task/lease. Changed-build timings were approximately 4s on the phone
and 14s including virtual-display creation/removal. A cold-launch check exposed
asynchronous application enumeration: the harness now awaits selected results
through UI events before inspection, rather than assuming loading has finished.
Managed Desktop and physical-input/wired coverage remain
pending. This increment does not resolve OEM HOME ownership.

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
