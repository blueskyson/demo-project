package com.example.esgaward.openfga;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.openfga.language.DslToJsonTransformer;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.configuration.ClientListStoresOptions;
import dev.openfga.sdk.api.model.AuthorizationModel;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import dev.openfga.sdk.api.model.Store;
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest;

/**
 * Creates the {@link OpenFgaClient} and makes sure the store and the authorization model exist:
 * <ol>
 *   <li>find the store named {@code openfga.store-name}, or create it;</li>
 *   <li>compile {@code openfga/model.fga} (classpath) and write it as a new model version if it
 *       differs from the store's latest model;</li>
 *   <li>pin the client to that store and model id.</li>
 * </ol>
 * So editing {@code model.fga} and restarting the backend is all it takes to roll out a model change.
 */
@Configuration
@EnableConfigurationProperties(OpenFgaProperties.class)
public class OpenFgaConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenFgaConfig.class);
    private static final String MODEL_RESOURCE = "openfga/model.fga";

    @Bean
    public OpenFgaClient openFgaClient(OpenFgaProperties properties) throws Exception {
        OpenFgaClient client = new OpenFgaClient(new ClientConfiguration().apiUrl(properties.apiUrl()));

        String storeId = findOrCreateStore(client, properties.storeName());
        client.setStoreId(storeId);
        String modelId = ensureModel(client);
        client.setAuthorizationModelId(modelId);

        log.info("OpenFGA ready: store {} ({}), model {}", properties.storeName(), storeId, modelId);
        return client;
    }

    private static String findOrCreateStore(OpenFgaClient client, String name) throws Exception {
        for (Store store : client.listStores(new ClientListStoresOptions().name(name)).get().getStores()) {
            if (name.equals(store.getName())) {
                return store.getId();
            }
        }
        log.info("Creating OpenFGA store {}", name);
        return client.createStore(new CreateStoreRequest().name(name)).get().getId();
    }

    private static String ensureModel(OpenFgaClient client) throws Exception {
        // The SDK's model classes are Jackson 2 annotated (Spring Boot 4 itself uses Jackson 3).
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        WriteAuthorizationModelRequest local = mapper.readValue(
                new DslToJsonTransformer().transform(readModelDsl()), WriteAuthorizationModelRequest.class);

        AuthorizationModel latest = client.readLatestAuthorizationModel().get().getAuthorizationModel();
        if (latest != null && sameModel(mapper, local, latest)) {
            return latest.getId();
        }
        log.info("Writing new OpenFGA authorization model from {}", MODEL_RESOURCE);
        return client.writeAuthorizationModel(local).get().getAuthorizationModelId();
    }

    private static boolean sameModel(ObjectMapper mapper, WriteAuthorizationModelRequest local, AuthorizationModel latest) {
        return Objects.equals(local.getSchemaVersion(), latest.getSchemaVersion())
                && normalized(mapper, local.getTypeDefinitions()).equals(normalized(mapper, latest.getTypeDefinitions()))
                && normalized(mapper, local.getConditions()).equals(normalized(mapper, latest.getConditions()));
    }

    /**
     * The server echoes models back with default values filled in ({@code ""}, {@code []},
     * {@code {}}, {@code null}); drop those so an unchanged model compares equal.
     */
    private static JsonNode normalized(ObjectMapper mapper, Object value) {
        return stripDefaults(mapper.valueToTree(value));
    }

    private static JsonNode stripDefaults(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(field -> {
                JsonNode child = stripDefaults(field.getValue());
                if (!isDefault(child)) {
                    result.set(field.getKey(), child);
                }
            });
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> result.add(stripDefaults(child)));
            return result;
        }
        return node;
    }

    private static boolean isDefault(JsonNode node) {
        return node.isNull()
                || (node.isTextual() && node.asText().isEmpty())
                || (node.isContainerNode() && node.isEmpty());
    }

    private static String readModelDsl() throws IOException {
        try (InputStream in = new ClassPathResource(MODEL_RESOURCE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
