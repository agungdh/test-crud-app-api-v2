package id.my.agungdh.user.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request to full update user (PUT) - all fields required")
public record UserUpdateRequest(
        @Schema(description = "Username", examples = "johndoe", required = true) @NotBlank @Size(min = 3, max = 50) String username,
        @Schema(description = "Password", examples = "newpass123", required = true) @NotBlank @Size(min = 6, max = 100) String password,
        @Schema(description = "Full name", examples = "John Doe", required = true) @NotBlank @Size(min = 1, max = 100) String name
) {}
