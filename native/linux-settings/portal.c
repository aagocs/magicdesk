#include "portal.h"
#include <string.h>

#define NAME "org.freedesktop.portal.Desktop"
#define PATH "/org/freedesktop/portal/desktop"
#define SETTINGS "org.freedesktop.portal.Settings"
#define APPEARANCE "org.freedesktop.appearance"
#define GTK "org.gnome.desktop.interface"

static const char introspection[] =
    "<node><interface name='org.freedesktop.portal.Settings'>"
    "<method name='ReadAll'><arg type='as' direction='in'/><arg type='a{sa{sv}}' direction='out'/></method>"
    "<method name='ReadOne'><arg type='s' direction='in'/><arg type='s' direction='in'/><arg type='v' direction='out'/></method>"
    "<method name='Read'><arg type='s' direction='in'/><arg type='s' direction='in'/><arg type='v' direction='out'/></method>"
    "<signal name='SettingChanged'><arg type='s'/><arg type='s'/><arg type='v'/></signal>"
    "<property name='version' type='u' access='read'/>"
    "</interface><interface name='org.freedesktop.DBus.Introspectable'>"
    "<method name='Introspect'><arg type='s' direction='out'/></method>"
    "</interface><interface name='org.freedesktop.DBus.Properties'>"
    "<method name='Get'><arg type='s' direction='in'/><arg type='s' direction='in'/><arg type='v' direction='out'/></method>"
    "<method name='GetAll'><arg type='s' direction='in'/><arg type='a{sv}' direction='out'/></method>"
    "</interface></node>";

typedef struct { const char *space, *key, *signature; } Setting;
static const Setting entries[] = {
    {APPEARANCE, "color-scheme", "u"},
    {GTK, "gtk-theme", "s"},
    /* GTK 3 initializes font settings when it receives this portal key. Output scale is separate. */
    {GTK, "text-scaling-factor", "d"}
};

static dbus_bool_t value(DBusMessageIter *iter, const Setting *setting, unsigned scheme) {
    DBusMessageIter variant;
    if (!dbus_message_iter_open_container(iter, DBUS_TYPE_VARIANT, setting->signature, &variant)) return FALSE;
    dbus_bool_t ok;
    if (!strcmp(setting->signature, "u")) {
        dbus_uint32_t v = scheme;
        ok = dbus_message_iter_append_basic(&variant, DBUS_TYPE_UINT32, &v);
    } else if (!strcmp(setting->signature, "s")) {
        const char *v = scheme == 1 ? "Adwaita-dark" : "Adwaita";
        ok = dbus_message_iter_append_basic(&variant, DBUS_TYPE_STRING, &v);
    } else {
        double v = 1.0;
        ok = dbus_message_iter_append_basic(&variant, DBUS_TYPE_DOUBLE, &v);
    }
    return ok && dbus_message_iter_close_container(iter, &variant);
}

static dbus_bool_t entry(DBusMessageIter *iter, const Setting *setting, unsigned scheme) {
    DBusMessageIter item;
    return dbus_message_iter_open_container(iter, DBUS_TYPE_DICT_ENTRY, NULL, &item)
        && dbus_message_iter_append_basic(&item, DBUS_TYPE_STRING, &setting->key)
        && value(&item, setting, scheme)
        && dbus_message_iter_close_container(iter, &item);
}

static int matches(DBusMessage *request, const char *space) {
    DBusMessageIter args, patterns;
    dbus_message_iter_init(request, &args);
    dbus_message_iter_recurse(&args, &patterns);
    if (dbus_message_iter_get_arg_type(&patterns) == DBUS_TYPE_INVALID) return 1;
    do {
        const char *pattern;
        dbus_message_iter_get_basic(&patterns, &pattern);
        size_t n = strlen(pattern);
        if (!n || !strcmp(pattern, space)
                || (pattern[n - 1] == '*' && !strncmp(pattern, space, n - 1))) return 1;
    } while (dbus_message_iter_next(&patterns));
    return 0;
}

static DBusMessage *read_all(MdSettings *settings, DBusMessage *request) {
    if (!dbus_message_has_signature(request, "as"))
        return dbus_message_new_error(request, DBUS_ERROR_INVALID_ARGS, "Expected namespaces");
    DBusMessage *reply = dbus_message_new_method_return(request);
    if (!reply) return NULL;
    DBusMessageIter root, namespaces;
    dbus_message_iter_init_append(reply, &root);
    if (!dbus_message_iter_open_container(&root, DBUS_TYPE_ARRAY, "{sa{sv}}", &namespaces)) goto oom;
    const char *spaces[] = {APPEARANCE, GTK};
    for (unsigned i = 0; i < 2; i++) {
        if (!matches(request, spaces[i])) continue;
        DBusMessageIter ns, values;
        if (!dbus_message_iter_open_container(&namespaces, DBUS_TYPE_DICT_ENTRY, NULL, &ns)
                || !dbus_message_iter_append_basic(&ns, DBUS_TYPE_STRING, &spaces[i])
                || !dbus_message_iter_open_container(&ns, DBUS_TYPE_ARRAY, "{sv}", &values)) goto oom;
        for (unsigned j = 0; j < sizeof(entries) / sizeof(entries[0]); j++)
            if (!strcmp(entries[j].space, spaces[i]) && !entry(&values, entries + j, settings->scheme)) goto oom;
        if (!dbus_message_iter_close_container(&ns, &values)
                || !dbus_message_iter_close_container(&namespaces, &ns)) goto oom;
    }
    if (!dbus_message_iter_close_container(&root, &namespaces)) goto oom;
    return reply;
oom:
    dbus_message_unref(reply);
    return NULL;
}

static DBusMessage *read_one(MdSettings *settings, DBusMessage *request, int legacy) {
    const char *space, *key;
    if (!dbus_message_get_args(request, NULL, DBUS_TYPE_STRING, &space, DBUS_TYPE_STRING, &key, DBUS_TYPE_INVALID))
        return dbus_message_new_error(request, DBUS_ERROR_INVALID_ARGS, "Expected namespace and key");
    const Setting *setting = NULL;
    for (unsigned i = 0; i < sizeof(entries) / sizeof(entries[0]); i++)
        if (!strcmp(entries[i].space, space) && !strcmp(entries[i].key, key)) setting = entries + i;
    if (!setting) return dbus_message_new_error(request, "org.freedesktop.portal.Error.NotFound", "Unknown setting");
    DBusMessage *reply = dbus_message_new_method_return(request);
    if (!reply) return NULL;
    DBusMessageIter root, outer;
    dbus_message_iter_init_append(reply, &root);
    /* Read preserves the portal's legacy double variant; ReadOne returns a single variant. */
    if (legacy && !dbus_message_iter_open_container(&root, DBUS_TYPE_VARIANT, "v", &outer)) goto oom;
    if (!value(legacy ? &outer : &root, setting, settings->scheme)
            || (legacy && !dbus_message_iter_close_container(&root, &outer))) goto oom;
    return reply;
oom:
    dbus_message_unref(reply);
    return NULL;
}

static DBusMessage *properties(DBusMessage *request, int all) {
    const char *interface, *key = "version";
    dbus_bool_t valid = all ? dbus_message_get_args(request, NULL, DBUS_TYPE_STRING, &interface, DBUS_TYPE_INVALID)
        : dbus_message_get_args(request, NULL, DBUS_TYPE_STRING, &interface, DBUS_TYPE_STRING, &key, DBUS_TYPE_INVALID);
    if (!valid) return dbus_message_new_error(request, DBUS_ERROR_INVALID_ARGS, "Invalid property query");
    if (strcmp(interface, SETTINGS) || strcmp(key, "version"))
        return dbus_message_new_error(request, DBUS_ERROR_UNKNOWN_PROPERTY, "Unsupported interface or property");
    DBusMessage *reply = dbus_message_new_method_return(request);
    if (!reply) return NULL;
    DBusMessageIter root, dictionary;
    dbus_message_iter_init_append(reply, &root);
    Setting version = {SETTINGS, "version", "u"};
    if (all) {
        if (!dbus_message_iter_open_container(&root, DBUS_TYPE_ARRAY, "{sv}", &dictionary)
                || !entry(&dictionary, &version, 2)
                || !dbus_message_iter_close_container(&root, &dictionary)) goto oom;
    } else if (!value(&root, &version, 2)) goto oom;
    return reply;
oom:
    dbus_message_unref(reply);
    return NULL;
}

static DBusHandlerResult message(DBusConnection *bus, DBusMessage *request, void *data) {
    MdSettings *settings = data;
    if (dbus_message_is_signal(request, DBUS_INTERFACE_DBUS, "NameLost")) {
        const char *name;
        if (dbus_message_get_args(request, NULL, DBUS_TYPE_STRING, &name, DBUS_TYPE_INVALID) && !strcmp(name, NAME))
            settings->replaced = 1;
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }
    if (dbus_message_get_type(request) != DBUS_MESSAGE_TYPE_METHOD_CALL) return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    DBusMessage *reply;
    const char *path = dbus_message_get_path(request);
    if (!path || strcmp(path, PATH))
        reply = dbus_message_new_error(request, DBUS_ERROR_UNKNOWN_OBJECT, "Unknown object");
    else if (dbus_message_is_method_call(request, SETTINGS, "ReadAll")) reply = read_all(settings, request);
    else if (dbus_message_is_method_call(request, SETTINGS, "ReadOne")) reply = read_one(settings, request, 0);
    else if (dbus_message_is_method_call(request, SETTINGS, "Read")) reply = read_one(settings, request, 1);
    else if (dbus_message_is_method_call(request, DBUS_INTERFACE_PROPERTIES, "Get")) reply = properties(request, 0);
    else if (dbus_message_is_method_call(request, DBUS_INTERFACE_PROPERTIES, "GetAll")) reply = properties(request, 1);
    else if (dbus_message_is_method_call(request, DBUS_INTERFACE_INTROSPECTABLE, "Introspect")
            && dbus_message_has_signature(request, "")) {
        reply = dbus_message_new_method_return(request);
        const char *xml = introspection;
        if (reply && !dbus_message_append_args(reply, DBUS_TYPE_STRING, &xml, DBUS_TYPE_INVALID)) {
            dbus_message_unref(reply); reply = NULL;
        }
    } else reply = dbus_message_new_error(request, DBUS_ERROR_UNKNOWN_METHOD, "Only read-only Settings is provided");
    if (!reply) return DBUS_HANDLER_RESULT_NEED_MEMORY;
    dbus_bool_t sent = dbus_connection_send(bus, reply, NULL);
    dbus_message_unref(reply);
    return sent ? DBUS_HANDLER_RESULT_HANDLED : DBUS_HANDLER_RESULT_NEED_MEMORY;
}

int md_settings_open(MdSettings *settings, unsigned scheme) {
    *settings = (MdSettings){.scheme = scheme};
    DBusError error = DBUS_ERROR_INIT;
    settings->bus = dbus_bus_get_private(DBUS_BUS_SESSION, &error);
    dbus_error_free(&error);
    if (!settings->bus) return -1;
    dbus_connection_set_exit_on_disconnect(settings->bus, FALSE);
    dbus_connection_set_max_message_size(settings->bus, 65536);
    dbus_connection_set_max_received_size(settings->bus, 262144);
    int result = dbus_bus_request_name(settings->bus, NAME,
            DBUS_NAME_FLAG_DO_NOT_QUEUE | DBUS_NAME_FLAG_ALLOW_REPLACEMENT, &error);
    dbus_error_free(&error);
    if (result != DBUS_REQUEST_NAME_REPLY_PRIMARY_OWNER) { md_settings_close(settings); return 0; }
    if (!dbus_connection_add_filter(settings->bus, message, settings, NULL)) { md_settings_close(settings); return -1; }
    return 1;
}

void md_settings_changed(MdSettings *settings, unsigned scheme) {
    if (settings->scheme == scheme) return;
    settings->scheme = scheme;
    for (unsigned i = 0; i < 2; i++) {
        DBusMessage *signal = dbus_message_new_signal(PATH, SETTINGS, "SettingChanged");
        if (!signal) continue;
        DBusMessageIter root;
        dbus_message_iter_init_append(signal, &root);
        if (dbus_message_iter_append_basic(&root, DBUS_TYPE_STRING, &entries[i].space)
                && dbus_message_iter_append_basic(&root, DBUS_TYPE_STRING, &entries[i].key)
                && value(&root, entries + i, scheme)) dbus_connection_send(settings->bus, signal, NULL);
        dbus_message_unref(signal);
    }
}

void md_settings_close(MdSettings *settings) {
    if (settings->bus) { dbus_connection_close(settings->bus); dbus_connection_unref(settings->bus); settings->bus = NULL; }
}
