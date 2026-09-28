package com.example.esgaward.openfga;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiUrl    OpenFGA HTTP API, e.g. {@code http://localhost:8090}
 * @param storeName store to use; created on startup if it doesn't exist
 * @param backfillOnStartup write tuples for all existing database rows on startup (idempotent)
 */
@ConfigurationProperties("openfga")
public record OpenFgaProperties(String apiUrl, String storeName, boolean backfillOnStartup) {
}
