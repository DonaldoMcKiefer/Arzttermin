package com.kiefer.arzttermin.domain;

public enum BillingType {
    STATUTORY("Kassentermin"),
    PRIVATE("Privattermin"),
    SELF_PAY("Selbstzahlertermin");

    private final String label;

    BillingType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
