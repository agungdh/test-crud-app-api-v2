package id.my.agungdh.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "User response")
public record UserResponse(
        @Schema(description = "User UUID") UUID uuid,
        @Schema(description = "Username", example = "johndoe") String username,
        @Schema(description = "Full name", example = "John Doe") String name,
        Instant createdAt,
        Instant updatedAt
) {}
