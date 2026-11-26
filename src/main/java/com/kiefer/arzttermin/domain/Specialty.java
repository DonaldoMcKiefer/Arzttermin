package com.kiefer.arzttermin.domain;

public enum Specialty {
    DERMATOLOGY("Hautarzt / Dermatologie"),
    GENERAL_MEDICINE("Allgemeinmedizin"),
    ORTHOPEDICS("Orthopädie"),
    GYNECOLOGY("Gynäkologie"),
    OPHTHALMOLOGY("Augenheilkunde"),
    ENT("Hals-Nasen-Ohren-Heilkunde");

    private final String label;

    Specialty(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
