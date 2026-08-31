package id.my.agungdh.auth.infrastructure.persistence;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class TokenStore {

    private static final String KEY_PREFIX = "auth:token:";

    @Inject
    RedisDataSource redisDataSource;

    private ValueCommands<String, String> values() {
        return redisDataSource.value(String.class, String.class);
    }

    public void store(String token, String userUuid, long ttlSeconds) {
        values().setex(key(token), ttlSeconds, userUuid);
    }

    public Optional<String> findUserUuid(String token) {
        String v = values().get(key(token));
        return Optional.ofNullable(v);
    }

    public void remove(String token) {
        if (token == null || token.isBlank()) return;
        redisDataSource.key().del(key(token));
    }

    public void refresh(String token, long ttlSeconds) {
        // expire keeps token alive on activity — optional sliding window
        redisDataSource.key().expire(key(token), java.time.Duration.ofSeconds(ttlSeconds));
    }

    private static String key(String token) {
        return KEY_PREFIX + token;
    }
}
