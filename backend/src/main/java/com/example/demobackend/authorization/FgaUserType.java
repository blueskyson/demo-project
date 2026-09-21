package com.example.demobackend.authorization;

/** OpenFGA subject type. Each constant's {@link #value()} must match a "type" entry in openfga/authorization-model.json. */
public enum FgaUserType {

    USER("user");

    private final String value;

    FgaUserType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
