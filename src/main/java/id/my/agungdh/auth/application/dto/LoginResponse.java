package id.my.agungdh.auth.application.dto;

import id.my.agungdh.user.application.dto.UserResponse;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Login response - token also set as HttpOnly cookie")
public record LoginResponse(
        @Schema(description = "Opaque token (also in cookie, for mobile Bearer)") String token,
        @Schema(description = "Authenticated user") UserResponse user
) {}
