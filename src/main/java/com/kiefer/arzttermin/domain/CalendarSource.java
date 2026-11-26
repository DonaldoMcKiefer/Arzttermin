package com.kiefer.arzttermin.domain;

public enum CalendarSource {
    LOCAL_DEMO("Lokaler Demo-Kalender");

    private final String label;

    CalendarSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
