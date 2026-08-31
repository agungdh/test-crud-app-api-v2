package id.my.agungdh.dto;

import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request to update user - at least one field required")
public record UserUpdateRequest(
        @Schema(description = "Username", example = "johndoe") @Size(min = 3, max = 50) String username,
        @Schema(description = "Password", example = "newpass123") @Size(min = 6, max = 100) String password,
        @Schema(description = "Full name", example = "John Doe") @Size(min = 1, max = 100) String name
) {}
