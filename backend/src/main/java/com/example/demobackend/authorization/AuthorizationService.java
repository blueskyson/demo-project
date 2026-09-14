package com.example.demobackend.authorization;

import java.util.List;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientListObjectsRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper over the OpenFGA client. Object identifiers use "type:id" tuples
 * (e.g. "document:1234"), matching OpenFGA's own convention, so callers just pass the
 * bare id and this class does the formatting.
 */
@Service
public class AuthorizationService {

    private final OpenFgaClient fgaClient;

    public AuthorizationService(OpenFgaClient fgaClient) {
        this.fgaClient = fgaClient;
    }

    public boolean check(String userType, String userId, String relation, String objectType, String objectId) {
        try {
            var request = new ClientCheckRequest()
                    .user(userRef(userType, userId))
                    .relation(relation)
                    ._object(objectRef(objectType, objectId));
            return Boolean.TRUE.equals(fgaClient.check(request).get().getAllowed());
        } catch (Exception e) {
            throw new IllegalStateException("OpenFGA check failed", e);
        }
    }

    public List<String> listObjectIds(String userId, String relation, String objectType) {
        try {
            var request = new ClientListObjectsRequest()
                    .user(userRef("user", userId))
                    .relation(relation)
                    .type(objectType);
            List<String> objects = fgaClient.listObjects(request).get().getObjects();
            return objects.stream().map(ref -> ref.substring(ref.indexOf(':') + 1)).toList();
        } catch (Exception e) {
            throw new IllegalStateException("OpenFGA listObjects failed", e);
        }
    }

    public void writeTuple(String userId, String relation, String objectType, String objectId) {
        try {
            var tuple = new ClientTupleKey()
                    .user(userRef("user", userId))
                    .relation(relation)
                    ._object(objectRef(objectType, objectId));
            fgaClient.write(new ClientWriteRequest().writes(List.of(tuple))).get();
        } catch (Exception e) {
            throw new IllegalStateException("OpenFGA write failed", e);
        }
    }

    private String userRef(String userType, String userId) {
        return userType + ":" + userId;
    }

    private String objectRef(String objectType, String objectId) {
        return objectType + ":" + objectId;
    }
}
