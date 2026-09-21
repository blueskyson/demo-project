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
public class OpenFgaAuthorizationService implements AuthorizationService {

    private final OpenFgaClient fgaClient;

    public OpenFgaAuthorizationService(OpenFgaClient fgaClient) {
        this.fgaClient = fgaClient;
    }

    @Override
    public boolean check(FgaUserType userType, String userId, FgaRelation relation, FgaObjectType objectType,
            String objectId) {
        try {
            var request = new ClientCheckRequest()
                    .user(userRef(userType, userId))
                    .relation(relation.value())
                    ._object(objectRef(objectType, objectId));
            return Boolean.TRUE.equals(fgaClient.check(request).get().getAllowed());
        } catch (Exception e) {
            throw new IllegalStateException("OpenFGA check failed", e);
        }
    }

    @Override
    public List<String> listObjectIds(String userId, FgaRelation relation, FgaObjectType objectType) {
        try {
            var request = new ClientListObjectsRequest()
                    .user(userRef(FgaUserType.USER, userId))
                    .relation(relation.value())
                    .type(objectType.value());
            List<String> objects = fgaClient.listObjects(request).get().getObjects();
            return objects.stream().map(ref -> ref.substring(ref.indexOf(':') + 1)).toList();
        } catch (Exception e) {
            throw new IllegalStateException("OpenFGA listObjects failed", e);
        }
    }

    @Override
    public void writeTuple(String userId, FgaRelation relation, FgaObjectType objectType, String objectId) {
        try {
            var tuple = new ClientTupleKey()
                    .user(userRef(FgaUserType.USER, userId))
                    .relation(relation.value())
                    ._object(objectRef(objectType, objectId));
            fgaClient.write(new ClientWriteRequest().writes(List.of(tuple))).get();
        } catch (Exception e) {
            throw new IllegalStateException("OpenFGA write failed", e);
        }
    }

    private String userRef(FgaUserType userType, String userId) {
        return userType.value() + ":" + userId;
    }

    private String objectRef(FgaObjectType objectType, String objectId) {
        return objectType.value() + ":" + objectId;
    }
}
