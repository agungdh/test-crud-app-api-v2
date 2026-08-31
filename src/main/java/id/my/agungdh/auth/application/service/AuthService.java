package id.my.agungdh.auth.application.service;

import id.my.agungdh.auth.application.dto.LoginRequest;
import id.my.agungdh.auth.application.dto.LoginResponse;
import id.my.agungdh.auth.infrastructure.persistence.TokenStore;
import id.my.agungdh.auth.infrastructure.security.TokenGenerator;
import id.my.agungdh.common.infrastructure.security.Argon2Hasher;
import id.my.agungdh.user.application.mapper.UserMapper;
import id.my.agungdh.user.domain.model.User;
import id.my.agungdh.user.infrastructure.persistence.UserRepository;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RunOnVirtualThread
public class AuthService {

    @Inject
    UserRepository userRepository;

    @Inject
    UserMapper userMapper;

    @Inject
    TokenStore tokenStore;

    @ConfigProperty(name = "app.auth.token-ttl-seconds", defaultValue = "604800")
    long tokenTtlSeconds;

    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new WebApplicationException("Invalid credentials", Response.Status.UNAUTHORIZED));

        // soft-deleted users are filtered by repository -> 401
        if (!Argon2Hasher.verify(req.password(), user.password)) {
            throw new WebApplicationException("Invalid credentials", Response.Status.UNAUTHORIZED);
        }

        String token = TokenGenerator.generate();
        tokenStore.store(token, user.uuid.toString(), tokenTtlSeconds);

        return new LoginResponse(token, userMapper.toResponse(user));
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        tokenStore.remove(token);
    }

    public Optional<User> validateToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Optional<String> uuidStr = tokenStore.findUserUuid(token);
        if (uuidStr.isEmpty()) return Optional.empty();
        try {
            UUID uuid = UUID.fromString(uuidStr.get());
            Optional<User> userOpt = userRepository.findByUuid(uuid);
            if (userOpt.isEmpty()) {
                // user deleted -> invalidate token
                tokenStore.remove(token);
                return Optional.empty();
            }
            // optional sliding expiration: refresh TTL on each request
            // tokenStore.refresh(token, tokenTtlSeconds);
            return userOpt;
        } catch (IllegalArgumentException e) {
            tokenStore.remove(token);
            return Optional.empty();
        }
    }
}
