package id.my.agungdh.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "User response")
public record UserResponse(
        @Schema(description = "User UUID") UUID uuid,
        @Schema(description = "Username", examples = "johndoe") String username,
        @Schema(description = "Full name", examples = "John Doe") String name
) {}
