package com.example.esgaward.awardevent;

import java.time.Instant;

public record AwardEventDto(
        Long id,
        String name,
        String description,
        Instant deadline,
        boolean closed,
        Instant createdAt,
        Instant updatedAt) {

    public static AwardEventDto from(AwardEvent event, Instant now) {
        return new AwardEventDto(event.getId(), event.getName(), event.getDescription(), event.getDeadline(),
                event.isClosedAt(now), event.getCreatedAt(), event.getUpdatedAt());
    }
}
