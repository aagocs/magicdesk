package io.github.mekhontsev.magicdesk;

import java.util.List;
import java.util.Objects;

/** A native panel intent. Its stable local identity owns geometry, never task or input authority. */
public record ShellPanel(String id, Edge edge, ShellAppearance.PanelStyle style,
        List<ShellComposition.Component> components) {
    public enum Edge {
        TOP, BOTTOM, LEFT, RIGHT;
        public boolean vertical() { return this == LEFT || this == RIGHT; }
    }
    public ShellPanel {
        if (id == null || !id.matches("[a-z][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("Invalid panel id");
        Objects.requireNonNull(edge); Objects.requireNonNull(style);
        components = List.copyOf(components);
        if (components.isEmpty() || components.size() > 24) throw new IllegalArgumentException("Panel needs 1-24 components");
    }
    public ShellPanel withStyle(ShellAppearance.PanelStyle value) { return new ShellPanel(id, edge, value, components); }
    public ShellPanel withComponents(List<ShellComposition.Component> value) { return new ShellPanel(id, edge, style, value); }
}
