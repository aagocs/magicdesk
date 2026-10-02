# Repository Instructions

Re-read this file after context compaction or session recovery before continuing
repository work. Preserve unrelated uncommitted changes; Git history changes
require the user's authorization.


## Fork Product Direction — Read First

This fork has a deliberate UX direction that overrides upstream assumptions when
the two conflict: **the phone should keep behaving like the user's normal OEM
phone, while an external/portable MagicDesk session should behave like a
conventional desktop workstation.** Read `docs/product-direction.md` before
changing session ownership, HOME/launcher behavior, display power, input routing,
taskbar/Start behavior, window interactions, or large-screen defaults.

Then read `docs/ux-work-plan.md` for the source audit, work-item IDs, next steps
and acceptance matrix. Wired mouse/keyboard and portable PC/scrcpy sessions are
equal-priority targets for this fork. Keep that plan current when work starts or
finishes; distinguish source findings, proposals and device-verified behavior.
The long-term target is a daily Windows/Linux replacement, including local PC
gaming. Read `docs/workstation-gaming.md` before compatibility/graphics/game work;
describe generic engineering capabilities, not personal application choices. Do not
claim Windows-game support from working Android or Linux desktop presentation.
Use `docs/testing-workflow.md` for focused checks between edits, then retain the
full verification and boundary-specific device gates below. Reuse authorized
connections and matching test signatures rather than repeating destructive setup.

Treat these as product invariants:

- An external, simulated, or portable Desktop must not unnecessarily replace the
  user's preferred phone HOME/launcher or turn display 0 into a MagicDesk-specific
  phone experience. Starting a Desktop on the phone itself is an explicit,
  separate choice.
- Phone Home/Recents, notifications, keyguard/lock screen, app switching and
  ordinary fullscreen phone applications should remain OEM/system behavior during
  an external-only session wherever Android permits it.
- Phone screen power and virtual/external display liveness are separate concerns.
  A portable workstation should not go black merely because the physical phone
  panel turns off; Android secure-content and keyguard policy must still be
  respected.
- Mouse and keyboard interaction on a workstation target should follow familiar
  Windows/Linux conventions. The phone touchpad is optional, not the primary
  model for controlling a Desktop from a PC.
- Desktop UI should optimize for direct manipulation, keyboard navigation,
  predictable window semantics and low setup friction rather than reproducing
  phone-first interaction patterns on a large display.

The current package-wide HOME lease for every managed Desktop is **implementation
debt for this fork, not the desired external-session product contract**. Do not
paper over it by force-launching an OEM launcher while MagicDesk still owns HOME;
change the lifecycle/ownership model deliberately and preserve the existing
runtime-layer guardrails.

## Multi-agent coordination

Several agents (different models and environments) work on this fork in
parallel. GitHub Issues are the coordination channel; start from the board
index on [issue #4](https://github.com/aagocs/magicdesk/issues/4). The stable
work IDs in the UX implementation plan remain the technical record and evidence
log. Before starting, check open issues and open branches/PRs for a claim on the
same work ID or files.

- Claim one issue by comment (agent, branch) before working; use your own branch
  (`codex/*`, `claude/*`, ...) and never push to another agent's branch.
- Choose work by tier: `tier:deep` (ownership/lifecycle/framework redesign,
  root-causing device failures), `tier:standard` (bounded change in one owning
  boundary, audits, new host fixtures), `tier:light` (docs, bookkeeping, adding
  existing tests to a host suite).
- Choose work by environment: `env:device` only with a verified authorized
  device; `env:host` needs only JDK/Python; `env:ci` needs the Android build
  (use repository CI without a local SDK). Device-less agents should produce
  probes, reproductions and fixtures that keep device sessions short.
- Finish with a handoff comment: what changed, verified where (host/CI/device),
  what is pending and the next step. Post only anonymized aggregate results.

## Public repository privacy

Never publish personal information in source, documentation, commits, PR bodies,
comments, screenshots or CI uploads. Public plans describe generic engineering
capabilities, not an individual's game choices, hardware inventory or setup.
Keep credentials, connection/network identifiers, serials, local paths, accounts,
application inventories and complete device/UI reports in private ignored
storage. Before any push, review the tracked diff and public text for personal
content; publish only anonymized aggregate verification results. Do not copy
private evidence into artifacts or logs merely because a test passed.

## Device Support Work

The MagicDesk APK targets Android 14 / API 34 and newer. Managed Desktop
requires Android 15 / API 35 and newer. Preserve both paths: newer Desktop
APIs must not become startup requirements for automation or independent tools.
Do not add support below API 34 or change either minimum without an explicit
decision. Build configuration is the installability contract; a successful
build is not proof of compatibility with an untested Android release.

## Runtime Layers

Before changing shared startup or service prerequisites, read the Runtime
Layers section of `docs/architecture.md` and `docs/runtime-api-levels.md`.
MCP, files, content, profiles, shell execution and Termux sessions are shared
services, not Desktop-owned features. Ordinary built-in Activity placement and
virtual-display creation must not acquire HOME, provision Desktop, initialize
its task/session coordinators, or require a WMShell Desktop backend.
Display input is a shared, explicitly acquired service. Opening an application
does not claim input. Desktop acquires it after preparation; manual selection
can control another display without Desktop or its shortcut filter.

Use `ToolApplications` and `ToolLaunchTarget` for built-in placement and
`DisplayOperations` for display resources. A display, its viewer and a Desktop
session have independent lifetimes. Close Desktop does not implicitly remove
its display. Closing or detaching an ordinary terminal window retains its PTY.
An explicitly managed tmux window releases only its client PTY; tmux owns the
server session and programs. MCP is an authorized adapter to these services, not their owner.
Keep profile-scoped application identities and storage boundaries intact.

Embedded X11 and Wayland are shared services. An explicitly selected Termux or
shell executor supplies programs; protocol runtimes own the X server or Wayland
compositor, while `hosted-runtime` owns shared graphics and frame presentation.
Shell-hosted display servers run as MagicDesk's app UID; a prepared chroot is entered
by the already-authorized command service, never by elevating the renderer.
MagicDesk's `x11-runtime` owns all X11 Java, Binder and JNI adapters; do not import
upstream Java, AIDL, Android stubs or Gradle modules. The native `embedded.h`
contract must not know Java classes, packages, authorization or Activity policy.
Android hosts borrow outputs; whole-desktop viewer closure retains the session, whereas an
individual-client host requests that client's closure. Keep clipboard, drag URI
grants and Android placement in the host, not the native renderer. Read
`docs/x11.md`, `docs/wayland.md`, `docs/graphics.md` and the X11 fork's
`docs/embedding.md` before changing this boundary.
`HostedSurfaceView` and `HostedContentExchange` are Android adapters: keep X11
button codes, target names, window IDs and XDND in their X11 backends, and
Wayland seat/data-device semantics in their Wayland adapters. The common Linux
graphics manager must not acquire viewer outputs, clipboard ownership or density
ownership. Shell contributions and dependent windows use the shared model in
`docs/shell-layout.md`; they do not replace Android task-area topology.

Do not retain obsolete internal APIs, persisted-data formats or MCP protocols
solely for backward compatibility unless explicitly requested. Remove replaced
paths instead of adding migration layers; this does not relax the supported
Android release boundaries.

`RuntimeCapabilities` owns service prerequisites, separately from MCP client
grants. Keep the full MCP tool catalog discoverable; unavailable operations
must fail explicitly without disabling independent services or changing system
state. On API 34, reject Desktop and its self-tests before display preparation
or HOME changes. Prefer ordinary app APIs when sufficient; use the shared
privileged service otherwise. UID 2000 remains the baseline. Shizuku and direct
`su` are startup transports, not alternative feature implementations. Keep
backend selection and the independent RuntimeLimits access ceiling at startup; never
silently elevate or switch identity for an individual operation. Root is not
a product requirement. Read `docs/privilege-modes.md` before changing that boundary.

## Platform Changes

Before changing platform, display, window, input, launcher, or cleanup behavior,
read `CONTRIBUTING.md`, `docs/ai-assisted-device-porting.md`, and the relevant
sections of `docs/architecture.md`, `docs/compatibility.md`, and
`docs/automation.md`.

Establish a current-`main` baseline from a complete compatibility report and an
exact reproduction before editing. Classify the owning boundary first. Keep one
APK and one codebase: prefer shared Android behavior and runtime capabilities;
isolate genuine firmware behavior in a focused `PlatformExtension`, SoC
behavior in `SocDisplayModeBackend`, display lifecycle in the existing four
drivers, and window policy in the existing transition gateway. Do not add model
checks, fixed runtime delays, coordinate-based production actions, root
requirements, or device-specific forks.

Keep Android-release differences in `FrameworkRuntime` and its focused
adapters. `FrameworkWindowingApi` is the only owner of hidden
`WindowContainerTransaction` primitives; `FrameworkWindowingCompat` owns
release-dependent semantics and polyfills; `HiddenTaskApi` owns raw task
members; `FrameworkTaskSnapshotSource` publishes typed Binder snapshots; and
`FrameworkInputWindowObservationSource` owns SurfaceFlinger input-window
commit events, while `FrameworkInputSnapshotSource` owns one-shot
InputDispatcher snapshots. Callers must
not reflect these APIs, pass raw framework member names, or introduce text task
queries themselves. An unavailable framework observation remains unknown; do
not synthesize a value that can be mistaken for an application request. Use
the debug-only
`MAGICDESK_FRAMEWORK_OVERRIDE` profile to exercise older semantics on a newer
device. Framework task observation belongs to
`FrameworkTaskObservationSource`; policy consumers may reuse its typed
snapshots but must not add another periodic task query.

Every wait must identify its mechanism and reason. State polling belongs in
`BoundedStateAwaiter`, event-driven monitor waits in `EventDrivenWaits`, and
intentional protocol/backoff/gesture delays in `RuntimeDelays`. Do not call
`Thread.sleep`, `SystemClock.sleep`, or `Object.wait` directly. Prefer an
existing callback or observer; the 150 ms framework task snapshot is the one
documented active-session fallback, not a general polling interval.
Mark bounded event/callback waits with an `EVENT_WAIT` comment naming the event
and timeout outcome. A timeout is a failure/cancellation bound, not a settling
delay or evidence that the awaited state was reached.

Use semantic MCP actions and event-driven waits when available. Interactive
self-tests require an awake, unlocked device and must retain production paths
and assertions. Do not weaken a test to make a device pass. Close an active
desktop through its production cleanup path before installing another APK.

## Local Device Automation

Verify the current environment before following device-specific instructions.
The configured MCP endpoint and maintainer phone setup described below are not
guaranteed to exist in another agent's checkout. Discover available tools and
authorized devices first; do not invent device coverage or replace another
user's MCP configuration. Use the environment preflight and evidence contract in
`docs/ux-work-plan.md` for fork UX work.

Never reboot the phone without asking the user and receiving explicit
confirmation immediately before the reboot command. An earlier discussion or
agreement that a reboot may be needed later does not count as confirmation.

After a phone reboot, start Shizuku with
`./scripts/start-shizuku-shell.sh` before privileged testing. This is the
canonical launcher: it starts the service as UID 2000 in the adb-shell SELinux
domain with the verified supplementary groups. Run it only after a reboot or
when Shizuku is unavailable; do not replace it with ad hoc `su` or `adb`
commands or restart a healthy service.

The MagicDesk MCP connection is already configured in `~/.codex/config.toml`.
Do not add a duplicate server or replace its endpoint or token unless the user
has reset the MCP token or application data. The server lives in the MagicDesk
process. After a reboot, open MagicDesk once from its launcher; the process
exposes MCP even before Shizuku becomes ready and promotes the same runtime
after Shizuku starts. If MCP tools are absent, first search the complete tool
catalog for the deferred `mcp__magicdesk__` tools; they may be omitted from the
short tool declaration shown in the session. If the complete catalog contains
them, MCP is available immediately: call those tools directly. If that search
is empty, verify that MagicDesk is open and **Local MCP automation server**
remains enabled, then ask the user to run `/mcp reload` when the current Codex
client supports it. Slash commands are user-side TUI actions and cannot be
invoked by the agent. After the user confirms the reload, search the catalog
again. Request one `resume` only when live reload is unavailable or the
refreshed catalog still omits MagicDesk. Reopening MagicDesk after an APK
reinstall reuses the existing MCP configuration.

For remote devices, use the configured MCP connection directly when available;
ADB is a bootstrap/recovery transport, not a requirement for normal automation.
Use the existing upload/download and APK-update protocol. Never repeat an
accepted installation merely because its connection dropped; resolve its exact
update ID and verify the returned build and process identity after reconnect.

An accepted self-test request is not a started or passed test. Observe its exact
run ID through startup, execution and cleanup; an observation-wait timeout does
not cancel the run. Never substitute a previous saved report for the current run.

## Fullscreen and Focus Work

Before changing fullscreen, task focus, Alt+Tab, taskbar activation, or task
display-area ownership, read `docs/fullscreen-transitions.md`. Taskbar,
Alt+Tab, overview, and MCP focus must continue through the same
`DesktopTaskController` gateway. Activation and demotion change only z-order;
they never hide, minimize, reparent, resize, or change an application's
windowing mode.

Use the same ownership model on every target. The HOME host and freeform tasks
remain in Android's standard root workspace; every fullscreen task retains one
stable organizer plane for its complete fullscreen residency. Every shell
transaction has one explicit owner, and steady-state focus only reorders the
existing hierarchy. Do not reintroduce a session-wide application task area or
remove an owned desktop display until its transitions are quiescent. Low-level
transition boundaries and rejected approaches are documented in
`docs/fullscreen-transitions.md`.

Before completing such a change, run the focused unit tests and the simulated
and phone desktop self-tests. Changes to shared task-area ownership or display
lifecycle also require the wired self-test. Required self-tests must retain
zero failures; never weaken their assertions to accommodate an implementation
change.

Changes confined to shared tools or automation require focused unit tests and
their own end-to-end workflows without Desktop. Desktop self-tests do not verify
that isolation and cannot run on API 34. For a minimum-SDK change, run Lint and
build checks at the actual minimum and report any missing device coverage;
masking Android 15 semantics on a newer phone does not emulate Android 14.

## Releases

When the user asks to publish a MagicDesk release, treat the release tag and
the next development cycle as one task.

1. Verify that the tag matches `magicDeskVersionName` in `gradle.properties`.
2. Build, test, tag, push, and verify the signed GitHub release.
3. Choose the next development version with the user if it was not already
   specified.
4. Run `scripts/start-next-version.sh VERSION VERSION_CODE`, commit the version
   change, and push it.

Do not report the release task complete while the version on `main` still
matches the newest release tag. Do not increment the version for ordinary
pushes; CI adds a unique development-build suffix automatically.
