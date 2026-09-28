package com.example.esgaward.openfga;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.stereotype.Component;

import com.example.esgaward.exception.AuthorizationUnavailableException;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientListObjectsRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.api.configuration.ClientWriteOptions;
import dev.openfga.sdk.api.model.WriteRequestDeletes;
import dev.openfga.sdk.api.model.WriteRequestWrites;

/**
 * Blocking wrapper around the async {@link OpenFgaClient}. Any OpenFGA failure becomes an
 * {@link AuthorizationUnavailableException} (HTTP 503), so callers fail closed.
 */
@Component
public class OpenFgaGateway {

    /** OpenFGA's default limit of tuples per Write request. */
    private static final int MAX_TUPLES_PER_WRITE = 100;

    private final OpenFgaClient client;

    public OpenFgaGateway(OpenFgaClient client) {
        this.client = client;
    }

    public boolean check(String user, String relation, String object, List<ClientTupleKey> contextualTuples,
            Map<String, Object> context) {
        ClientCheckRequest request = new ClientCheckRequest()
                .user(user)
                .relation(relation)
                ._object(object)
                .contextualTuples(contextualTuples)
                .context(context);
        return Boolean.TRUE.equals(await(() -> client.check(request)).getAllowed());
    }

    /** Ids of all objects of {@code type} the user has {@code relation} on. */
    public List<String> listObjects(String user, String relation, String type, List<ClientTupleKey> contextualTuples,
            Map<String, Object> context) {
        ClientListObjectsRequest request = new ClientListObjectsRequest()
                .user(user)
                .relation(relation)
                .type(type)
                .contextualTupleKeys(contextualTuples)
                .context(context);
        return await(() -> client.listObjects(request)).getObjects();
    }

    /** Writes tuples; tuples that already exist (with the same condition) are ignored. */
    public void write(List<ClientTupleKey> tuples) {
        ClientWriteOptions options = new ClientWriteOptions().onDuplicate(WriteRequestWrites.OnDuplicateEnum.IGNORE);
        for (List<ClientTupleKey> chunk : chunks(tuples)) {
            await(() -> client.write(ClientWriteRequest.ofWrites(chunk), options));
        }
    }

    /** Deletes tuples; tuples that don't exist are ignored. */
    public void delete(List<ClientTupleKeyWithoutCondition> tuples) {
        ClientWriteOptions options = new ClientWriteOptions().onMissing(WriteRequestDeletes.OnMissingEnum.IGNORE);
        for (List<ClientTupleKeyWithoutCondition> chunk : chunks(tuples)) {
            await(() -> client.write(ClientWriteRequest.ofDeletes(chunk), options));
        }
    }

    private static <T> List<List<T>> chunks(List<T> items) {
        List<List<T>> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i += MAX_TUPLES_PER_WRITE) {
            chunks.add(items.subList(i, Math.min(i + MAX_TUPLES_PER_WRITE, items.size())));
        }
        return chunks;
    }

    private static <T> T await(FgaCall<T> call) {
        try {
            return call.execute().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthorizationUnavailableException(e);
        } catch (ExecutionException e) {
            throw new AuthorizationUnavailableException(e.getCause());
        } catch (Exception e) {
            throw new AuthorizationUnavailableException(e);
        }
    }

    @FunctionalInterface
    private interface FgaCall<T> {
        CompletableFuture<T> execute() throws Exception;
    }
}
