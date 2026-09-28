package io.github.mekhontsev.magicdesk;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Same optional settings-helper contract for native Termux and explicitly entered Linux guests. */
final class LinuxAppearanceLaunch {
    private final String helper;
    private final Map<String, String> environment;
    private final boolean guest;

    LinuxAppearanceLaunch(String nativeDirectory, boolean enabled, boolean guest, int scheme) {
        this.guest = guest;
        helper = nativeDirectory + "/libmagicdesk_linux_settings.so";
        if (!enabled) { environment = Map.of(); return; }
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("MAGICDESK_APPEARANCE_SOCKET", "magicdesk-appearance-" + UUID.randomUUID());
        values.put("MAGICDESK_APPEARANCE_TOKEN", java.util.HexFormat.of().formatHex(secret));
        values.put("MAGICDESK_COLOR_SCHEME", Integer.toString(
                io.github.mekhontsev.magicdesk.hosted.HostedColorScheme.require(scheme)));
        environment = java.util.Collections.unmodifiableMap(values);
    }
    void configure(Map<String, String> target) { target.putAll(environment); }
    String exports() {
        if (environment.isEmpty()) return "";
        StringBuilder result = new StringBuilder(" MAGICDESK_APPEARANCE_HELPER=").append(q(helper));
        environment.forEach((key, value) -> result.append(' ').append(key).append('=').append(q(value)));
        return result.toString();
    }
    String command(String command, String shell) {
        if (environment.isEmpty() || guest) return command;
        return "exec " + q(helper) + " -- " + q(shell) + " -c " + q(command);
    }
    private static String q(String value) { return ShellCommandLine.quote(value); }
}
