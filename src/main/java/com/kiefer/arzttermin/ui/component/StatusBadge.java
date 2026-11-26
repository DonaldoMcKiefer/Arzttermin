package com.kiefer.arzttermin.ui.component;

import com.vaadin.flow.component.html.Span;

/** Compact, consistent status indicator for the three portal workspaces. */
public class StatusBadge extends Span {

    public enum Tone {
        NEUTRAL, INFO, SUCCESS, WARNING, DANGER
    }

    public StatusBadge(String text, Tone tone) {
        super(text);
        addClassNames("status-badge", "status-" + tone.name().toLowerCase());
    }

    public static StatusBadge of(String text, Tone tone) {
        return new StatusBadge(text, tone);
    }
}
