package com.kiefer.arzttermin.domain;

public enum AppointmentType {
    SKIN_CANCER_SCREENING("Hautkrebs-Screening"),
    CONSULTATION("Sprechstunde / Beratung"),
    ACUTE("Akutbehandlung"),
    CHECKUP("Vorsorgeuntersuchung"),
    FOLLOW_UP("Kontrolltermin");

    private final String label;

    AppointmentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
