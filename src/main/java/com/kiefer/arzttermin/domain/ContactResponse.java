package com.kiefer.arzttermin.domain;

public enum ContactResponse {
    NO_RESPONSE("Keine Rückmeldung"),
    DECLINED("Kein Termin verfügbar"),
    AVAILABLE("Termin verfügbar"),
    CALLBACK("Rückruf vereinbart");

    private final String label;

    ContactResponse(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
