#!/bin/sh
set -eu
: "${MAGICDESK_WAYLAND_RUNTIME:?Run inside a MagicDesk Wayland session}"
: "${WAYLAND_DISPLAY:?Missing Wayland display}"
demo_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec proot-distro login --isolated \
    --bind "$MAGICDESK_WAYLAND_RUNTIME:/tmp/magicdesk-wayland" \
    --bind "$demo_dir:/opt/magicdesk-waybar-demo" \
    --env "WAYLAND_DISPLAY=/tmp/magicdesk-wayland/$WAYLAND_DISPLAY" \
    ubuntu -- /bin/sh /opt/magicdesk-waybar-demo/guest.sh
