package id.my.agungdh.auth.application.dto;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Login request")
public record LoginRequest(
        @Schema(description = "Username", examples = "johndoe", required = true) @NotBlank String username,
        @Schema(description = "Password", examples = "secret123", required = true) @NotBlank String password
) {}
