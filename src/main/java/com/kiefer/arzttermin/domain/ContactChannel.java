package com.kiefer.arzttermin.domain;

public enum ContactChannel {
    PHONE("Telefon"),
    EMAIL("E-Mail"),
    FAX("Fax");

    private final String label;

    ContactChannel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
