package com.example.demobackend.authorization;

/** OpenFGA object type. Each constant's {@link #value()} must match a "type" entry in openfga/authorization-model.json. */
public enum FgaObjectType {

    DOCUMENT("document");

    private final String value;

    FgaObjectType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
