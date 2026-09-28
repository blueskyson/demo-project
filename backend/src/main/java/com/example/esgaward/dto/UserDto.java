package com.example.esgaward.dto;

import java.util.UUID;

import com.example.esgaward.entity.User;
import com.example.esgaward.entity.UserRole;

public record UserDto(UUID id, String username, String email, String displayName, UserRole role) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(), user.getRole());
    }
}
