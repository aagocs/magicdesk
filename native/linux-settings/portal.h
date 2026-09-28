#pragma once
#include <dbus/dbus.h>

/* The bus implementation belongs to libdbus; this adapter exports only read-only settings. */
typedef struct { DBusConnection *bus; unsigned scheme; int replaced; } MdSettings;
int md_settings_open(MdSettings *settings, unsigned scheme);
void md_settings_changed(MdSettings *settings, unsigned scheme);
void md_settings_close(MdSettings *settings);
