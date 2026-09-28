package com.example.esgaward.proposal;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(@NotNull UUID userId) {
}
