package id.my.agungdh.entity;

import java.time.Instant;
import java.util.UUID;

public record UserRow(
        long id,
        UUID uuid,
        String username,
        String passwordHash,
        String name,
        Instant createdAt,
        Long createdBy,
        Instant updatedAt,
        Long updatedBy,
        Instant deletedAt,
        Long deletedBy
) {}
