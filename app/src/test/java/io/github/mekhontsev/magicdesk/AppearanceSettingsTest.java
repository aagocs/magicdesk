package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public final class AppearanceSettingsTest {
    @Test public void editingLocalTypographyDoesNotFreezeGlobalPanelsOrColors() throws Exception {
        var before = ShellAppearance.defaults();
        var after = before.withTypography(new ShellAppearance.Typography(ShellAppearance.Font.MONO, 1));
        String changed = AppearanceSettings.changedPatch("{\"motion\":{\"reduced\":true}}", before, after);
        var patch = new org.json.JSONObject(changed);
        assertFalse(patch.has("composition"));
        assertFalse(patch.has("colors"));
        assertTrue(patch.getJSONObject("motion").getBoolean("reduced"));
        assertEquals("mono", patch.getJSONObject("typography").getString("font"));
        assertFalse(patch.getJSONObject("typography").has("scale"));
        var resolved = WorkspaceAppearancePatch.parse(changed).resolve(ShellAppearance.preset("light"));
        assertEquals(ShellAppearance.preset("light").palette(), resolved.palette());
        assertEquals(ShellAppearance.Font.MONO, resolved.typography().font());
    }
    private static ShellPanel panel(String id, ShellComposition.Kind... kinds) {
        return new ShellPanel(id, ShellPanel.Edge.BOTTOM, ShellAppearance.PanelStyle.defaults(),
                java.util.Arrays.stream(kinds).map(ShellComposition.Component::of).toList());
    }

    @Test public void movingSingletonPreservesIdentityAndLeavesValidSource() {
        var original = new ShellComposition(List.of(panel("dock", ShellComposition.Kind.START),
                panel("status", ShellComposition.Kind.CLOCK)), ShellComposition.Start.defaults());
        var moved = AppearanceSettings.moveComponent(original, "dock", 0, "status");
        assertEquals("dock", moved.panels().get(0).id());
        assertEquals(ShellComposition.Kind.SPACER, moved.panels().get(0).components().get(0).type());
        assertEquals(List.of(ShellComposition.Kind.CLOCK, ShellComposition.Kind.START),
                moved.panels().get(1).components().stream().map(ShellComposition.Component::type).toList());
        assertEquals(ShellComposition.Kind.START, original.panels().get(0).components().get(0).type());
    }

    @Test public void fullOrMissingDestinationCannotLoseSourceComponent() {
        var full = new ShellPanel("full", ShellPanel.Edge.LEFT, ShellAppearance.PanelStyle.defaults(),
                java.util.Collections.nCopies(24, ShellComposition.Component.of(ShellComposition.Kind.SPACER)));
        var original = new ShellComposition(List.of(panel("dock", ShellComposition.Kind.START), full), ShellComposition.Start.defaults());
        assertThrows(IllegalArgumentException.class, () -> AppearanceSettings.moveComponent(original, "dock", 0, "full"));
        assertThrows(IllegalArgumentException.class, () -> AppearanceSettings.moveComponent(original, "dock", 0, "missing"));
        assertThrows(IllegalArgumentException.class, () -> AppearanceSettings.moveComponent(original, "dock", 0, "dock"));
        assertEquals(ShellComposition.Kind.START, original.panels().get(0).components().get(0).type());
    }

    @Test public void addingPanelNeverRenumbersExistingIds() {
        var panels = List.of(panel("panel-1", ShellComposition.Kind.START), panel("panel-3", ShellComposition.Kind.CLOCK));
        assertEquals("panel-2", AppearanceSettings.nextPanelId(panels));
        assertEquals("panel-3", panels.get(1).id());
    }
}
