package com.example.esgaward.user;

import java.util.UUID;

public record UserDto(UUID id, String username, String email, String displayName, UserRole role) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(), user.getRole());
    }
}
