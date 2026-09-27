package io.github.mekhontsev.magicdesk;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Native shell contents, independent of Views, service permissions and task ownership. */
public record ShellComposition(List<Component> taskbar, Start start) {
    public enum Kind {
        START, TASKS, SHOW_DESKTOP, OPEN_TASKS, NOTIFICATIONS, KEYBOARD_LAYOUT,
        PHONE_SCREEN, QUICK_CONTROLS, BATTERY, CLOCK, SPACER
    }
    public enum Visibility { ALWAYS, EXPANDED, EXTERNAL }
    public enum Clock { TIME, DATE, DATE_TIME }
    public record Component(Kind type, int widthDp, int minViewportDp,
            Visibility visibility, String label, Clock clock) {
        public Component {
            Objects.requireNonNull(type); Objects.requireNonNull(visibility);
            Objects.requireNonNull(label); Objects.requireNonNull(clock);
            ShellAppearance.range(widthDp, 0, 240, "component width");
            if (widthDp > 0 && widthDp < 32) throw new IllegalArgumentException("component width must be 0 or at least 32");
            ShellAppearance.range(minViewportDp, 0, 4096, "minimum viewport");
            if (label.length() > 32 || label.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("component label must be at most 32 printable characters");
            }
            if (!label.isEmpty() && type != Kind.START) throw new IllegalArgumentException("Only Start has a configurable label");
            if (clock != Clock.TIME && type != Kind.CLOCK) throw new IllegalArgumentException("Only Clock has a clock format");
        }
        public boolean visible(boolean compact, boolean external, int viewportDp) {
            return viewportDp >= minViewportDp && switch (visibility) {
                case ALWAYS -> true;
                case EXPANDED -> !compact;
                case EXTERNAL -> external && !compact;
            };
        }
        public static Component of(Kind kind) {
            return new Component(kind, 0, 0, kind == Kind.PHONE_SCREEN ? Visibility.EXTERNAL
                    : kind == Kind.KEYBOARD_LAYOUT ? Visibility.EXPANDED : Visibility.ALWAYS, "", Clock.TIME);
        }
    }
    public enum Section { RECENT, APPS, RUNNING, TOOLS }
    public enum Presentation { GRID, LIST }
    public record Start(List<Section> sections, Presentation presentation, int tileWidthDp, int iconSizeDp) {
        public Start {
            sections = List.copyOf(sections); Objects.requireNonNull(presentation);
            if (!sections.contains(Section.APPS) || new HashSet<>(sections).size() != sections.size()) {
                throw new IllegalArgumentException("Start sections must include apps and must not repeat");
            }
            ShellAppearance.range(tileWidthDp, 80, 200, "tile width");
            ShellAppearance.range(iconSizeDp, 24, 64, "icon size");
        }
        public static Start defaults() {
            return new Start(List.of(Section.RECENT, Section.APPS, Section.RUNNING, Section.TOOLS),
                    Presentation.GRID, 100, 44);
        }
    }
    public ShellComposition {
        taskbar = List.copyOf(taskbar); Objects.requireNonNull(start);
        if (taskbar.isEmpty() || taskbar.size() > 24) throw new IllegalArgumentException("Taskbar needs 1-24 components");
        var seen = new HashSet<Kind>();
        for (Component component : taskbar) {
            if (component.type() != Kind.SPACER && !seen.add(component.type())) {
                throw new IllegalArgumentException("Repeated taskbar component: " + component.type());
            }
        }
    }
    public static ShellComposition defaults() {
        return new ShellComposition(java.util.Arrays.stream(Kind.values())
                .filter(k -> k != Kind.SPACER).map(Component::of).toList(), Start.defaults());
    }
}
