package com.example.demobackend.authorization;

import java.util.List;

/**
 * Authorization primitives used by {@link FgaCheckAspect} and by services that need
 * OpenFGA queries beyond a single-object check (e.g. {@code ListObjects}). Kept as an
 * interface, separate from {@link OpenFgaAuthorizationService}, so callers can be unit
 * tested against a mock/fake without a real OpenFGA server.
 */
public interface AuthorizationService {

    boolean check(FgaUserType userType, String userId, FgaRelation relation, FgaObjectType objectType,
            String objectId);

    List<String> listObjectIds(String userId, FgaRelation relation, FgaObjectType objectType);

    void writeTuple(String userId, FgaRelation relation, FgaObjectType objectType, String objectId);
}
