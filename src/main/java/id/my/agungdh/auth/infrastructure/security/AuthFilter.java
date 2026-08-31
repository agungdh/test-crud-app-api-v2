package id.my.agungdh.auth.infrastructure.security;

import id.my.agungdh.auth.application.service.AuthService;
import id.my.agungdh.user.domain.model.User;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(AuthFilter.class);

    // paths that don't require authentication
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/auth/login",
            "/auth/logout",
            "/q/openapi",
            "/q/swagger-ui",
            "/q/health",
            "/q/metrics"
    );
    private static final Set<String> PUBLIC_EXACT = Set.of(
            "/openapi",
            "/swagger-ui"
    );

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "app.auth.cookie-name", defaultValue = "access_token")
    String cookieName;

    @Inject
    AuthService authService;

    @Inject
    AuthContext authContext;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        // normalize: getPath() may not start with '/', add it
        if (!path.startsWith("/")) path = "/" + path;

        if (isPublic(path, requestContext.getMethod())) {
            return;
        }

        String token = extractToken(requestContext);

        if (token == null || token.isBlank()) {
            abortUnauthorized(requestContext, "Missing authentication token");
            return;
        }

        Optional<User> userOpt = authService.validateToken(token);
        if (userOpt.isEmpty()) {
            abortUnauthorized(requestContext, "Invalid or expired token");
            return;
        }

        User user = userOpt.get();
        authContext.setUser(user);
        authContext.setToken(token);

        // optional: set security context for downstream
        // requestContext.setSecurityContext(...)
        LOG.debugf("Authenticated %s for %s %s", user.username, requestContext.getMethod(), path);
    }

    private boolean isPublic(String path, String method) {
        // CORS preflight always public
        if ("OPTIONS".equalsIgnoreCase(method)) return true;
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        for (String exact : PUBLIC_EXACT) {
            if (path.equals(exact)) return true;
        }
        // allow exact /auth/login only; other /auth/* need auth
        return false;
    }

    public String extractToken(ContainerRequestContext ctx) {
        // 1) Cookie
        Cookie cookie = ctx.getCookies().get(cookieName);
        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
            return cookie.getValue();
        }
        // 2) Authorization: Bearer <token>
        String auth = ctx.getHeaderString("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String t = auth.substring(7).trim();
            if (!t.isEmpty()) return t;
        }
        return null;
    }

    private void abortUnauthorized(ContainerRequestContext ctx, String message) {
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(java.util.Map.of("error", message))
                .build());
    }
}
