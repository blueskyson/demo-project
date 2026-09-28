package com.example.esgaward.architecture.fixture.service;

/**
 * Deliberately broken fixture for {@code ServiceArchitectureTest}: a public method in a
 * {@code ..service..} package without {@code @RequirePermission}. Not a Spring bean.
 */
public class UnannotatedService {

    public void resetPassword(Long userId) {
    }
}
