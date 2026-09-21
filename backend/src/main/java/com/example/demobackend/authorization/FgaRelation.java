package com.example.demobackend.authorization;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** OpenFGA relation. Each constant's {@link #value()} must match a relation name in openfga/authorization-model.json. */
public enum FgaRelation {

    OWNER("owner"),
    EDITOR("editor"),
    VIEWER("viewer");

    private final String value;

    FgaRelation(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Lets client-supplied strings (e.g. request bodies) be validated into a relation at deserialization time. */
    @JsonCreator
    public static FgaRelation fromValue(String value) {
        return Arrays.stream(values())
                .filter(relation -> relation.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown FGA relation: " + value));
    }
}
