package com.example.demobackend.config;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCreateStoreResponse;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import dev.openfga.sdk.api.model.Store;
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Bootstraps the OpenFGA store + authorization model on application startup, the same
 * "self-initializing on boot" pattern as the in-memory H2 schema. OpenFGA store IDs are
 * server-generated, so there's no way to pre-declare a fixed ID the way Keycloak's realm
 * import does with a realm name — instead this looks up a store by name and reuses it,
 * or creates one, then (re)writes the authorization model from the bundled JSON resource.
 */
@Configuration
public class OpenFgaConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenFgaConfig.class);
    private static final int MAX_STARTUP_ATTEMPTS = 15;
    private static final long RETRY_DELAY_MS = 2000;

    @Bean
    public OpenFgaClient openFgaClient(
            @Value("${openfga.api-url}") String apiUrl,
            @Value("${openfga.store-name}") String storeName) throws Exception {

        ClientConfiguration config = new ClientConfiguration().apiUrl(apiUrl);
        OpenFgaClient client = new OpenFgaClient(config);

        String storeId = findOrCreateStore(client, storeName);
        config = config.storeId(storeId);
        client.setConfiguration(config);

        String modelId = writeAuthorizationModel(client);
        config = config.authorizationModelId(modelId);
        client.setConfiguration(config);

        log.info("OpenFGA ready: store={} model={}", storeId, modelId);
        return client;
    }

    private String findOrCreateStore(OpenFgaClient client, String storeName) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_STARTUP_ATTEMPTS; attempt++) {
            try {
                List<Store> stores = client.listStores().get().getStores();
                for (Store store : stores) {
                    if (storeName.equals(store.getName())) {
                        return store.getId();
                    }
                }
                ClientCreateStoreResponse created =
                        client.createStore(new CreateStoreRequest().name(storeName)).get();
                return created.getId();
            } catch (Exception e) {
                lastError = e;
                log.warn("OpenFGA not reachable yet (attempt {}/{}): {}", attempt, MAX_STARTUP_ATTEMPTS,
                        e.getMessage());
                Thread.sleep(RETRY_DELAY_MS);
            }
        }
        throw new IllegalStateException(
                "Could not reach OpenFGA after " + MAX_STARTUP_ATTEMPTS + " attempts", lastError);
    }

    private String writeAuthorizationModel(OpenFgaClient client) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        WriteAuthorizationModelRequest request;
        try (var input = new ClassPathResource("openfga/authorization-model.json").getInputStream()) {
            request = objectMapper.readValue(input, WriteAuthorizationModelRequest.class);
        }
        return client.writeAuthorizationModel(request).get().getAuthorizationModelId();
    }
}
