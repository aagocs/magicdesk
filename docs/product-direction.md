# Fork Product Direction

This document is the normative user-experience direction for the
`aagocs/magicdesk` fork. Read it together with `AGENTS.md` and
`docs/architecture.md`: the architecture document explains how MagicDesk works
today; this document explains what this fork is trying to become.

The [UX implementation plan](ux-work-plan.md) is the execution companion: it
records the source audit, work-item status, next steps and validation matrix.
Wired monitor/mouse/keyboard and portable PC/scrcpy sessions have equal priority;
success on one does not establish success on the other.

## Vision: two coherent surfaces, one device

MagicDesk should let one Android device serve two roles at the same time without
forcing either role to imitate the other.

1. **The phone remains an ordinary OEM Android phone.**
2. **The workstation target behaves like a conventional desktop computer.**

The long-term outcome is a full daily workstation that can replace a
Windows/Linux computer for supported work and local PC gaming. Conventional shell
interaction is the first layer; application compatibility, file workflows,
graphics, audio, input, persistence and recovery are part of the goal. The
[workstation and gaming roadmap](workstation-gaming.md) stages that work, including
generic PC application support. It distinguishes
implemented foundations from compatibility that remains unverified.

The goal is not to clone Windows pixels or replace Android's task/window system.
The goal is to preserve native Android capabilities underneath a UI and lifecycle
that are unsurprising for both phone use and desktop use.

## Product invariant 1: the phone stays OEM-like

An external, simulated, wireless, wired, or portable/virtual Desktop is not a
reason to replace the user's phone experience.

Target behavior:

- Starting an external-only Desktop does **not** change the user's preferred HOME
  app on display 0.
- Home and Recents on the phone keep resolving to the OEM/system behavior.
- Notifications, Quick Settings, keyguard/lock screen, calls, app switching and
  normal fullscreen phone apps continue to work as they do outside MagicDesk.
- MagicDesk's phone control panel remains available as an app/notification
  surface, not as a mandatory replacement launcher.
- The phone touchpad is an optional controller for another display. It is not the
  default interaction model for someone already using a mouse and keyboard.
- Starting a Desktop on the phone itself is a separate explicit action and may
  legitimately use different HOME/windowing mechanics from an external session.
- Phone panel power and workstation-display liveness are independent. Turning
  off or locking the physical phone should not blank an unrelated owned virtual
  display simply because display 0 is dark. Android security, secure surfaces and
  keyguard restrictions must still be respected.

### Current mismatch

The current managed-session model acquires one package-wide Android HOME lease
for every Desktop. For an external target, MagicDesk exposes
`PhoneHomeActivity` on display 0 and hosts `DesktopActivity` on the secondary
display. This is a coherent upstream architecture, but it is not this fork's
desired external-session UX.

For this fork, package-wide HOME ownership during an external-only session is
**implementation debt**.

Do not treat "launch the OEM launcher directly while MagicDesk still owns HOME"
as the solution. The ownership/lifecycle contract itself should be redesigned so
normal phone navigation does not depend on a competing MagicDesk HOME surface.

## Product invariant 2: the workstation behaves like a desktop

The workstation surface should be understandable to a Windows, Linux, or macOS
desktop user without requiring them to mentally translate phone interactions.

### Pointer and keyboard

- A physical or remotely captured mouse directly controls the target Desktop
  pointer.
- Pointer capture/grab and release are explicit and predictable in scrcpy/remote
  workflows.
- Left click, right click, middle click, wheel, hover, double-click, drag and
  modifier-click behave conventionally where the target application supports
  them.
- Keyboard focus follows the active window.
- Common task/window shortcuts use one consistent semantic path.
- The phone touchpad remains available, but desktop users should not need to move
  a cursor on the phone screen to control the external workspace.

### Windows and task switching

- Focus, activation, minimize/conceal, maximize/restore, fullscreen, snap,
  move and resize have distinct predictable meanings.
- Alt+Tab, taskbar activation and overview switch exact running tasks without
  recreating Activities.
- Android, X11, Wayland, terminal and built-in MagicDesk windows should feel like
  peers in one workspace.
- Dialogs and settings shown on a large workstation target should use dense,
  desktop-appropriate layouts rather than oversized phone-first interaction.

### Start, taskbar and desktop surface

- Start/application search should be fast, keyboard-friendly and scoped to the
  intended target display.
- Running and pinned state should be visually clear.
- Context menus, desktop files, launchers and drag/drop should follow familiar
  desktop conventions.
- Desktop defaults should make a fresh session useful with minimal setup.
  Advanced MagicDesk-specific controls remain accessible without dominating the
  primary workflow.

## Architecture guardrails

The fork direction changes product priorities, not the project's engineering
discipline.

Keep these upstream strengths:

- Android applications remain real Android tasks.
- WMShell/framework task ownership stays behind the existing typed boundaries.
- Shared tools, displays, viewers, terminals, Linux runtimes, input and Desktop
  retain independent lifetimes.
- Android 14 remains the APK floor and managed Desktop keeps its documented API
  boundary unless explicitly changed.
- One codebase and runtime capability probing are preferred over device forks.
- Security/keyguard behavior must not be bypassed to make remote viewing easier.
- Do not add sleeps, coordinate automation, model checks, a second task organizer,
  or hidden-API reflection outside the existing framework adapters.

When current architecture and target UX conflict, first identify the real Android
constraint, then change the narrowest owning boundary. Do not preserve a poor UX
solely because it is already encoded in the current lifecycle.

## Prioritized implementation backlog

Until issue tracking is enabled on this fork, the [UX implementation
plan](ux-work-plan.md) is the canonical implementation backlog and status record.
The sections below define its product priorities and outcomes.

### P0 — Decouple external Desktop from the phone HOME role

**Outcome:** wired, wireless, simulated and portable/virtual Desktop sessions can
start, run, close and recover without replacing the preferred HOME app on display
0.

Work through `DesktopHomeRoleLease`, `DesktopHomeSurfaceRouter`,
`DesktopSessionController`, `DesktopSessionTransitionCoordinator`, the display
drivers and recovery tests. Determine the minimum framework/WMShell mechanism
needed to host the external workspace without package-wide primary-HOME takeover.

Acceptance criteria:

1. preferred HOME package/component before start, during external session and
   after close are identical;
2. phone Home/Recents retain OEM/system behavior;
3. external Desktop host and managed windows still function;
4. process death/recovery cannot leave MagicDesk as primary HOME;
5. an explicitly selected phone Desktop still works.

### P0 — Keep portable/virtual Desktop alive when the phone panel turns off

**Outcome:** a MagicDesk-owned virtual display captured by scrcpy or a viewer
continues to produce frames when the physical phone display is dark, subject to
Android secure-content/keyguard rules.

Trace `ShellVirtualDisplays`, output Surface/ImageReader ownership, phone screen
power policy, keyguard composition and display-token cleanup. Separate physical
screen retention from virtual-display liveness.

Acceptance criteria:

1. remote Desktop continues updating with display 0 off where Android permits;
2. lock/unlock behavior is documented and never bypasses secure-content policy;
3. closing Desktop does not implicitly remove the virtual display;
4. explicit display removal still owns token teardown.

### P0 — Desktop-class pointer and keyboard control

**Outcome:** controlling a portable/external Desktop from a PC feels like direct
desktop input rather than a phone touchpad workaround.

Audit `DisplayInputSession`, `RuntimeDisplayInputCoordinator`,
`RuntimeInputCoordinator`, pointer routing, the uinput bridge, shortcut filtering
and scrcpy capture behavior. Define one repeatable matrix for USB/Bluetooth and
remote-view input.

Acceptance criteria include pointer routing/capture, keyboard focus, Alt+Tab,
window drag/resize, context click, wheel scrolling, multi-button input and clean
recovery when a display/session changes.

### P1 — Desktop shell interaction audit and incremental fixes

Audit taskbar, Start, overview, context menus, window actions, Files, Settings,
terminal, X11 and Wayland presentation against the desktop invariants above.
Split findings into focused implementation units. Favor interaction consistency
over visual imitation of any one operating system.

### P1 — Simplify workstation defaults

Review fresh-session defaults and the control/settings surfaces. Common desktop
use should require fewer MagicDesk-specific decisions, while expert controls stay
available. Large-display defaults should prioritize information density,
keyboard navigation and predictable placement.

## Agent workflow

Use [faster development checks](testing-workflow.md) between edits, and preserve
the full final verification gate. Keep progress tied to measurable workflows,
including the workstation/gaming stages, rather than visual resemblance alone.

For any user-facing session or shell change:

1. classify the behavior as **phone**, **desktop**, or **shared runtime**;
2. check this document before assuming the current UI is intentional;
3. preserve OEM phone behavior unless the user explicitly selected a phone
   Desktop;
4. prefer conventional desktop behavior on workstation targets;
5. keep lifecycle ownership separate: display, viewer, input, Desktop and phone
   screen are not the same resource;
6. add focused tests for the owning boundary and run the device coverage required
   by `AGENTS.md`.

When a tradeoff is unavoidable, document the Android/framework constraint and
the least-surprising fallback. "That is how the current MagicDesk UI works" is
not sufficient justification by itself.
