package io.github.mekhontsev.magicdesk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class AutomationAppearance {
    static DesktopAutomationResult execute(String command, JSONObject arguments) throws JSONException {
        switch (command) {
            case "appearance.get" -> { }
            case "appearance.apply" -> AppearanceStore.apply(ShellAppearanceJson.parse(arguments.getJSONObject("document").toString()));
            case "appearance.preset" -> AppearanceStore.apply(AppearanceStore.current().withStyle(ShellAppearance.preset(arguments.getString("name"))));
            case "appearance.reset" -> AppearanceStore.apply(ShellAppearance.defaults());
            default -> throw new IllegalArgumentException("Unknown appearance operation");
        }
        return DesktopAutomationResult.success("ok", new JSONObject()
                .put("document", ShellAppearanceJson.encode(AppearanceStore.current()))
                .put("presets", new JSONArray().put("dark").put("light").put("contrast")));
    }
}
