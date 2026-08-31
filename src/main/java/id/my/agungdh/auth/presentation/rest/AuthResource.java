package id.my.agungdh.auth.presentation.rest;

import id.my.agungdh.auth.application.dto.LoginRequest;
import id.my.agungdh.auth.application.dto.LoginResponse;
import id.my.agungdh.auth.application.service.AuthService;
import id.my.agungdh.auth.infrastructure.security.AuthContext;
import id.my.agungdh.user.application.dto.UserResponse;
import id.my.agungdh.user.application.mapper.UserMapper;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
@Tag(name = "Auth", description = "Authentication - cookie + bearer")
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    AuthContext authContext;

    @Inject
    UserMapper userMapper;

    @ConfigProperty(name = "app.auth.cookie-name", defaultValue = "access_token")
    String cookieName;

    @ConfigProperty(name = "app.auth.cookie-secure", defaultValue = "false")
    boolean cookieSecure;

    @ConfigProperty(name = "app.auth.cookie-same-site", defaultValue = "Lax")
    String cookieSameSite;

    @ConfigProperty(name = "app.auth.cookie-path", defaultValue = "/")
    String cookiePath;

    @ConfigProperty(name = "app.auth.cookie-max-age-seconds", defaultValue = "604800")
    int cookieMaxAge;

    @POST
    @Path("/login")
    @Operation(summary = "Login", description = "Username/password -> opaque token in Valkey + HttpOnly cookie. Also returns token for mobile Bearer.")
    @APIResponse(responseCode = "200", description = "Login success", content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @APIResponse(responseCode = "401", description = "Invalid credentials")
    public Response login(@Valid LoginRequest req) {
        LoginResponse resp = authService.login(req);

        NewCookie cookie = buildCookie(resp.token(), cookieMaxAge);
        return Response.ok(resp)
                .cookie(cookie)
                .build();
    }

    @POST
    @Path("/logout")
    @Operation(summary = "Logout", description = "Invalidate token in Valkey and clear cookie. Accepts cookie or Bearer token.")
    @APIResponse(responseCode = "204", description = "Logged out")
    public Response logout(@Context HttpHeaders headers, @CookieParam("access_token") String cookieToken) {
        String token = cookieToken;
        if (token == null || token.isBlank()) {
            String auth = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
            if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                token = auth.substring(7).trim();
            }
        }
        // also try from AuthContext if filter already populated
        if ((token == null || token.isBlank()) && authContext.getToken() != null) {
            token = authContext.getToken();
        }

        authService.logout(token);

        // clear cookie
        NewCookie clearCookie = buildCookie("", 0);
        return Response.noContent()
                .cookie(clearCookie)
                .build();
    }

    @GET
    @Path("/me")
    @Operation(summary = "Get current user", description = "Requires auth via cookie or Bearer")
    @APIResponse(responseCode = "200", description = "Current user", content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @APIResponse(responseCode = "401", description = "Not authenticated")
    public UserResponse me() {
        if (!authContext.isAuthenticated()) {
            throw new NotAuthorizedException("Not authenticated");
        }
        return userMapper.toResponse(authContext.getUser());
    }

    private NewCookie buildCookie(String value, int maxAge) {
        // JAX-RS NewCookie with SameSite via extension attribute (Quarkus/RESTEasy supports it)
        // Build manually to ensure SameSite, HttpOnly, Secure, Path
        // Using NewCookie.Builder (Jakarta WS RS 3.1)
        NewCookie.SameSite sameSite;
        try {
            sameSite = NewCookie.SameSite.valueOf(cookieSameSite);
        } catch (Exception e) {
            sameSite = NewCookie.SameSite.LAX;
        }

        return new NewCookie.Builder(cookieName)
                .value(value)
                .path(cookiePath)
                .maxAge(maxAge)
                .secure(cookieSecure)
                .httpOnly(true)
                .sameSite(sameSite)
                .build();
    }
}
