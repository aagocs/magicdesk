# Workstation and PC application compatibility

The long-term goal is a daily workstation that can replace a Windows/Linux
computer for supported workflows, including local PC gaming. This extends
[product direction](product-direction.md) without changing the OEM-phone
invariants or making root a requirement. Wired and portable sessions remain
equal priorities. [UX work items](ux-work-plan.md) track the shell foundations.

## Current support and capability requirements

Local Windows applications and PC games are not yet verified. Existing Android,
terminal, X11 and Wayland support provides foundations. Desktop presentation and
an accelerated compositor do not establish graphics acceleration inside a
Linux/Windows client. Read [graphics](graphics.md), [X11](x11.md) and
[Wayland](wayland.md) before changing this boundary.

Choose compatibility layers from actual executable architecture, graphics API,
client-driver capabilities and supported runtime versions. Probe capabilities
rather than recognizing device models. An Android system Vulkan version is not
proof of a working Linux client Vulkan ICD, Wine integration or presentation.

Current [DXVK driver requirements](https://github.com/doitsujin/dxvk/wiki/Driver-support)
specify Vulkan 1.4 and additional features; Android/mobile graphics drivers have
no first-party support. Current [vkd3d-proton requirements](https://github.com/HansKristian-Work/vkd3d-proton)
specify Vulkan 1.3 plus required features. Legacy layers with lower version
floors do not establish compatibility with a current application. Requirements
change: pin and verify the actual version set, driver features and workload.
Device-specific observations belong in private evidence, not this public roadmap.

The next experiment is a small capability/rendering probe in an explicitly
prepared Linux environment before installing a large application. Prove the
client driver, required features and real frame presentation. Preserve one
codebase and runtime capability reporting across current and future hardware.

## Ownership and implementation approach

Keep executable environments separate from the shell. Use existing selected
Termux/shell executors and configured environments. X11/Wayland own protocols,
`hosted-runtime` owns shared frame presentation, and Android hosts own input,
clipboard, URI grants and window placement. Application integration must not
create another task organizer or acquire phone HOME. A failed client must leave
independent tools and workstation cleanup usable.

A candidate Windows path uses Wine and matching CPU translation where needed,
then a supported graphics translation layer and actual client graphics driver
feeding the existing host. [Box64](https://github.com/ptitSeb/box64) translates
x86-64 Linux programs on ARM64; its
[Wine guide](https://github.com/ptitSeb/box64/blob/main/docs/WINE.md) describes
Wine/WoW64 setup. Box64 alone does not execute Windows PE programs.
[DXVK](https://github.com/doitsujin/dxvk) handles Direct3D 8/9/10/11;
[vkd3d-proton](https://github.com/HansKristian-Work/vkd3d-proton) addresses
Direct3D 12. Select the route from the actual client and driver capabilities,
and pin a reproducible environment. Do not bundle untested launchers or
GPU-specific patches in the APK to conceal missing environment support.

## Staged work and acceptance

| ID | Outcome | Evidence needed | Current status |
| --- | --- | --- | --- |
| WS-030 | Daily desktop workflows | Browser/editor/files/terminal together; open/save, clipboard, drag/drop, keyboard access, reconnect, persistence and cleanup on wired and portable sessions | Planned; foundations exist, full workflow unverified |
| GAME-001 | Compatibility preflight | CPU/driver/features, client ICD, environment/runtime versions, storage and explicit unsupported reasons | Planned; Linux client path unverified |
| GAME-010 | Native ARM64 3D workload | Correct animated frames, renderer identity, input, audio and cleanup on the intended display | Planned; depends on GAME-001 |
| GAME-020 | Small Windows graphics workload | Matching Wine/CPU translation, actual D3D API, correct pixels, held keys/buttons, pointer capture/release, audio and recovery | Planned; depends on GAME-010 |
| GAME-030 | Application launcher lifecycle | Interactive login, install/update/relaunch, persistent environment and useful failures; exact component versions | Planned; depends on GAME-020 |
| GAME-040 | Representative PC game | World/scene loading, correct rendering, input/audio, measured frame times and thermal behavior, graceful return to desktop | Planned; depends on GAME-030 |

Daily-workstation acceptance also includes dependable file access, application
installation/update, suspend/reconnect, display scaling, error recovery and
discoverable settings. Publish supported workflows and remaining gaps rather
than declaring operating-system replacement from a shell screenshot.

For gameplay, record resolution, settings, workload, session duration, frame-time
distribution and throttling in private evidence. Set practical performance targets
after the small workload runs; measure wired and portable transport latency
separately. Account credentials and entitlement remain interactive user actions.
Do not store credentials in fixtures or bypass application integrity checks.

Public documentation records generic engineering work, aggregate verification
and limitations. Never publish personal game choices, hardware inventory,
network identifiers, account data, private reports or local environment paths.
Keep full reproduction evidence in private storage and publish only a reviewed,
anonymized summary. Use [fast testing](testing-workflow.md) for short feedback;
large downloads and long gameplay tests belong after smaller stages pass.
