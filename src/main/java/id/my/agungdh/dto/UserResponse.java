package id.my.agungdh.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID uuid,
        String username,
        String name,
        Instant createdAt,
        Instant updatedAt
) {}
