# Waybar On Android Desktop

Unmodified Ubuntu Waybar uses `wlr-layer-shell` for its panel and
`wlr-foreign-toplevel-management` for the workspace's Android and Linux tasks.
The upper panel reserves space through MagicDesk's common shell layout.

Requirements: a running MagicDesk Desktop, working Termux integration and an
existing proot-distro Ubuntu installation. Install the panel in Termux:

```sh
proot-distro login ubuntu -- apt-get install --no-install-recommends waybar dbus
```

Create a retained Wayland session with the Termux executor in **Linux graphics**.
Bind its shell components to the desired Desktop workspace, then use **Run command**:

```sh
sh "$HOME/magicdesk-android/scripts/examples/waybar/launch.sh"
```

Adjust the repository path if needed. Through MCP, the equivalent sequence is
`graphics.start`, wait for `graphics_ready`, `graphics.set_workspace`, then
`graphics.execute`. Bind before launching Waybar: layer-shell admission belongs
to that workspace, not to an independent application window.

Open Android applications on the same Desktop. Left-click a Waybar task to
activate it, or demote it if active; right-click toggles maximization.
The configured actions go through the Wayland protocol and MagicDesk's shared
task controller, not shell commands embedded in the panel configuration.

Stop this graphics session to remove the panel without closing Android tasks.
The launcher mounts only the session socket directory and this configuration
into the existing distribution; it does not install a Linux desktop or require
root. The panel is restricted to the logical `MagicDesk` output, so individual
application render outputs do not create additional bars.
