# UX-010: Desktop HOME ownership design

Status: **source-investigated proposal; device probe pending.** Nothing here is
device-verified. Read [product direction](product-direction.md) and the
[UX plan](ux-work-plan.md#ux-010-external-workspace-ownership-without-phone-home-takeover)
first. This document separates three kinds of statement:

- **Source fact** — traced in this repository at the cited file.
- **Framework hypothesis** — expected AOSP behavior that must be confirmed on a
  device before any implementation depends on it.
- **Proposal** — the intended change, gated on the probe below.

## Today: one lease owns three different things

`DesktopHomeRoleLease` is a single persisted record that currently carries:

1. **Primary HOME role** (`android.app.role.HOME`). `activate` → `claim` adds
   MagicDesk as the role holder for every Desktop, external or phone, and
   restores the previous holder on release. *(source fact)*
2. **SECONDARY_HOME preference.** `FrameworkSecondaryHomeApi` replaces the
   user's preferred `SECONDARY_HOME` handler with `DesktopActivity` through
   `IPackageManager.replacePreferredActivity`, and restores the captured
   previous handler. This is a PackageManager preference, independent of the
   HOME role. *(source fact)*
3. **Session membership.** `targets`, `policy`, `compatibility`, `phase` and
   `closingDisplayId` are read by roughly twenty call sites
   (`DesktopShellActivity`, `RuntimeDesktopSessionCoordinator`,
   `DesktopTaskWatcher`, `FullscreenStartController`, automation state,
   diagnostics, self-test cleanup, …) as the authoritative "which Desktop
   sessions exist" registry. `isActiveForDisplay` requires `phase == ACTIVE`,
   which is only reached after the primary role claim succeeds. *(source fact)*

Component enablement couples them further: `DesktopHomeSurfaceRouter` enables
`PhoneHomeActivity` (primary HOME filter) on display 0 whenever any external
workspace exists, so the phone presents MagicDesk's launcher surface during an
external-only session. *(source fact)*

Phone Recents is coupled through `isPhoneOverviewRoutingActive`:
`ShellPhoneOverviewRouter` intercepts the firmware Recents component and
presents MagicDesk's phone HOME while the lease is active. *(source fact)*

## How the external host becomes a HOME task

`ShellDesktopHostLauncher` launches the explicit `DesktopActivity` component
through `TaskDisplayAreaLaunchCommand.launchFullscreenTask` with
`ACTIVITY_TYPE_HOME`, which `FrameworkActivityLaunchApi.setActivityType` passes
as `ActivityOptions.setLaunchActivityType`. It then verifies that the resulting
task really has `ACTIVITY_TYPE_HOME` and fails otherwise. *(source fact)*

**Framework hypothesis H1.** The HOME activity type of the host comes from the
launch options creating a HOME root task in the target display area, not from
the caller being the role holder. Per-activity HOME classification in AOSP
`ActivityRecord.setActivityType` is restricted for explicit-component launches
from non-system callers, so the task-level path is the plausible mechanism.
If H1 holds, an external HOME host can be admitted while the OEM launcher keeps
the primary role.

**Framework hypothesis H2 (risk).** When Android itself (re)starts HOME on a
secondary display — display added, host task removed, a HOME key/gesture
targeting that display — AOSP `RootWindowContainer.resolveSecondaryHomeActivity`
first looks for a `SECONDARY_HOME` activity **inside the primary HOME
package**, and only falls back to the preferred `SECONDARY_HOME` resolution
when none exists (or when `config_useSystemProvidedLauncherForSecondary` is
set). Many OEM launchers ship such an activity. Therefore, with the OEM
launcher keeping the primary role, MagicDesk's SECONDARY_HOME preference may
not win framework-initiated HOME starts on the workspace display. This is the
central unknown; it decides whether "preference only" is sufficient.

**Framework hypothesis H3.** Recents on display 0 resolves to the system/OEM
recents component when MagicDesk does not hold the primary role, so the phone
overview router simply stays disabled for external-only sessions.

## Proposal: split ownership by what each target needs

| Session shape | Primary HOME role | `PhoneHomeActivity` | SECONDARY_HOME preference | Phone Recents routing |
| --- | --- | --- | --- | --- |
| External-only (wired, wireless, portable/virtual) | **Not claimed** — OEM retained | Disabled | Claimed while any external workspace exists | Off |
| Explicit phone Desktop | Claimed (unchanged behavior) | Enabled | Claimed if an external workspace also exists | On |
| None | Not claimed | Disabled | Restored | Off |

Structural changes, in order:

1. **Extract session membership** from the HOME lease into a workspace
   registry with its own phase (`PREPARED/ACTIVE/RELEASING/RECOVERING`).
   Call sites that only ask "is a Desktop active on display N" read the
   registry. *Implemented (read side, behavior-neutral,
   [#6](https://github.com/aagocs/magicdesk/issues/6)):*
   `DesktopWorkspaceMembership` is now the only membership read path; the
   lease's own `isActiveForDisplay`/`isReleasingForDisplay` were removed.
   Direct `DesktopHomeRoleLease.snapshot()` readers are limited to HOME
   ownership, recovery and diagnostics by an allowlist guard in
   `DesktopWorkspaceMembershipTest`. Membership is still persisted inside the
   lease record; splitting storage moves to step 2
   ([#7](https://github.com/aagocs/magicdesk/issues/7)), where the two
   lifetimes first diverge and crash-recovery ordering between two records can
   be designed against real semantics rather than a no-op split.
2. **Make the primary role a sub-lease** acquired only by a
   `DesktopDisplayTarget.isDefaultWorkspace()` target and released when the
   last phone workspace closes, even if external workspaces continue. Its
   restoration rules (capture previous holder, honor a user's newer choice,
   `STARTUP_RELINQUISHED`) stay exactly as today.
3. **Keep the SECONDARY_HOME preference** as the workspace-wide sub-lease,
   restored when the last workspace closes.
4. **Router:** `DesktopHomeSurfaceRouter.Selection.primary` becomes
   `LAUNCHER` only when a phone workspace exists, not "any workspace exists".
5. **Recovery:** `DesktopHomeStartupGuard` and `reconcile` restore each
   sub-lease independently; a stale SECONDARY_HOME preference never causes a
   primary-role change.

If H2 fails (OEM secondary launcher wins framework-initiated HOME starts), the
bounded alternative inside the current task model is: keep the explicit host
launch (H1), and have the existing `DesktopTaskWatcher` treat an unexpected
foreign HOME task on an owned workspace display as a host-loss event handled by
the existing recovery path — **not** a new periodic query, and not a
force-launch of anything on display 0. Do not replace the host with an
ordinary fullscreen Activity (wallpaper, stacking and fullscreen-plane
behavior depend on HOME type; see [fullscreen transitions](fullscreen-transitions.md)).

## Device probe protocol (for an agent with an authorized device)

Run on API 35+ under shell UID 2000 with the existing runtime and MCP; no
second organizer, no root. Record pass/fail/unavailable per step and post only
anonymized results. Restore everything by exact identity afterward. Do not run
on a device whose user has not authorized HOME/preference changes.

| Step | Action | Observe | Answers |
| --- | --- | --- | --- |
| P0 | Record OEM role holder, SECONDARY_HOME resolution, whether the OEM package exports a `SECONDARY_HOME` activity, `config_useSystemProvidedLauncherForSecondary` | Values only (package names of system launchers are fine; no app inventory) | H2 precondition |
| P1 | With OEM holding the role and MagicDesk's components in today's prepared state (SECONDARY_HOME enabled), create an owned virtual display and launch the host via the existing launcher path **without** `activate`/role claim | Task created? `ACTIVITY_TYPE_HOME`? Wallpaper/stacking correct? | H1 |
| P2 | Same display: remove the host task by exact id, then let Android restart HOME (or send HOME to that display) | Which component becomes HOME on the workspace display | H2 |
| P3 | Repeat P2 after `claimSecondaryHome` (preference only, role untouched) | Does the preference win? | H2 |
| P4 | During P1: phone HOME key, Recents gesture, notification shade, open/close a fullscreen app on display 0 | OEM behavior unchanged? | Product invariant 1 / H3 |
| P5 | Cleanup: restore SECONDARY_HOME preference, disable components, remove display, release awake lease | Prior state byte-for-byte (role holder, preference) | Cleanup contract |

Report results on [#5](https://github.com/aagocs/magicdesk/issues/5) with
the build identity reference kept private.

## Acceptance

The UX plan's mixed-residency transition table and acceptance matrix remain the
acceptance criteria. This design adds one rule: **no external-only path may
call the primary-role claim.** A host fixture should assert that, with fake
backends, for every external-only start/join/close/failure/recovery sequence,
`setHomePackage`/`clearHomePackage` are never invoked and the captured role
holder is untouched.
