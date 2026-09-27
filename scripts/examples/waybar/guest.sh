#!/bin/sh
set -eu
umask 077
XDG_RUNTIME_DIR=$(mktemp -d /tmp/magicdesk-waybar.XXXXXX)
export XDG_RUNTIME_DIR
trap 'rm -rf -- "$XDG_RUNTIME_DIR"' EXIT
export XDG_SESSION_TYPE=wayland GDK_BACKEND=wayland GTK_THEME=Adwaita:dark
export NO_AT_BRIDGE=1
dbus-run-session -- waybar \
    --config /opt/magicdesk-waybar-demo/config.json \
    --style /opt/magicdesk-waybar-demo/style.css
