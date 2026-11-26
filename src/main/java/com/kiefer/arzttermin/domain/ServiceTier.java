package com.kiefer.arzttermin.domain;

public enum ServiceTier {
    AUTOMATIC_5("Automatische Suche", 500),
    PERSONAL_25("Persönliche Suche", 2_500);

    private final String label;
    private final int feeCents;

    ServiceTier(String label, int feeCents) {
        this.label = label;
        this.feeCents = feeCents;
    }

    public String getLabel() {
        return label;
    }

    public int getFeeCents() {
        return feeCents;
    }
}
