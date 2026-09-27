package io.github.mekhontsev.magicdesk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class AutomationAppearance {
    static DesktopAutomationResult execute(String command, JSONObject arguments) throws JSONException {
        switch (command) {
            case "appearance.get" -> { }
            case "appearance.schema" -> {
                return DesktopAutomationResult.success("ok", new JSONObject().put("schema", ShellAppearanceSchema.document()));
            }
            case "appearance.validate" -> {
                var value = ShellAppearanceJson.parse(arguments.getJSONObject("document").toString());
                return DesktopAutomationResult.success("valid", new JSONObject().put("document", ShellAppearanceJson.encode(value)));
            }
            case "appearance.preview" -> AppearanceStore.preview(ShellAppearanceJson.parse(arguments.getJSONObject("document").toString()));
            case "appearance.confirm" -> AppearanceStore.confirm(arguments.getString("previewId"));
            case "appearance.cancel" -> AppearanceStore.cancel(arguments.getString("previewId"));
            case "appearance.apply" -> AppearanceStore.apply(ShellAppearanceJson.parse(arguments.getJSONObject("document").toString()));
            case "appearance.preset" -> AppearanceStore.apply(AppearanceStore.current().withStyle(ShellAppearance.preset(arguments.getString("name"))));
            case "appearance.reset" -> AppearanceStore.apply(ShellAppearance.defaults());
            default -> throw new IllegalArgumentException("Unknown appearance operation");
        }
        var state = AppearanceStore.snapshot();
        return DesktopAutomationResult.success("ok", new JSONObject()
                .put("document", ShellAppearanceJson.encode(state.current()))
                .put("committed", ShellAppearanceJson.encode(state.committed()))
                .put("previewId", state.previewId() == null ? JSONObject.NULL : state.previewId())
                .put("revision", state.revision())
                .put("presets", new JSONArray().put("dark").put("light").put("contrast")));
    }
}
