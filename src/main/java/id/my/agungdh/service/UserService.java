package id.my.agungdh.service;

import id.my.agungdh.dto.PageResponse;
import id.my.agungdh.dto.UserCreateRequest;
import id.my.agungdh.dto.UserResponse;
import id.my.agungdh.dto.UserUpdateRequest;
import id.my.agungdh.entity.UserRow;
import id.my.agungdh.repository.UserRepository;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import id.my.agungdh.util.Argon2Hasher;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RunOnVirtualThread
public class UserService {

    @Inject
    UserRepository repository;

    private UserResponse toResponse(UserRow row) {
        return new UserResponse(row.uuid(), row.username(), row.name(), row.createdAt(), row.updatedAt());
    }

    public UserResponse create(UserCreateRequest req) {
        // Check unique username
        if (repository.findByUsername(req.username()).isPresent()) {
            throw new WebApplicationException("Username already exists", Response.Status.CONFLICT);
        }
        String hash = Argon2Hasher.hash(req.password());
        try {
            UserRow row = repository.insert(req.username(), hash, req.name(), null);
            return toResponse(row);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) {
                throw new WebApplicationException("Username already exists", Response.Status.CONFLICT);
            }
            // Also check message contains duplicate key
            if (e.getMessage() != null && e.getMessage().contains("ux_users_username_active")) {
                throw new WebApplicationException("Username already exists", Response.Status.CONFLICT);
            }
            throw e;
        }
    }

    public UserResponse findByUuid(UUID uuid) {
        UserRow row = repository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toResponse(row);
    }

    public PageResponse<UserResponse> list(int limit, String cursor) {
        int pageSize = Math.min(Math.max(limit, 1), 100);
        Instant cursorCreatedAt = null;
        Long cursorId = null;

        if (cursor != null && !cursor.isBlank()) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int sep = decoded.lastIndexOf(':');
                if (sep == -1) throw new IllegalArgumentException("Invalid cursor format");
                cursorCreatedAt = Instant.parse(decoded.substring(0, sep));
                cursorId = Long.parseLong(decoded.substring(sep + 1));
            } catch (Exception ex) {
                throw new WebApplicationException("Invalid cursor", Response.Status.BAD_REQUEST);
            }
        }

        List<UserRow> rows = repository.findAll(pageSize, cursorCreatedAt, cursorId);
        boolean hasNext = rows.size() > pageSize;
        List<UserRow> pageRows = hasNext ? rows.subList(0, pageSize) : rows;

        List<UserResponse> data = pageRows.stream().map(this::toResponse).toList();

        String nextCursor = null;
        if (hasNext) {
            UserRow last = pageRows.get(pageRows.size() - 1);
            String raw = last.createdAt().toString() + ":" + last.id();
            nextCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        return new PageResponse<>(data, nextCursor, hasNext);
    }

    public UserResponse update(UUID uuid, UserUpdateRequest req) {
        String passwordHash = null;
        if (req.password() != null && !req.password().isBlank()) {
            passwordHash = Argon2Hasher.hash(req.password());
        }

        // If username is being changed, check unique
        if (req.username() != null) {
            repository.findByUsername(req.username()).ifPresent(existing -> {
                if (!existing.uuid().equals(uuid)) {
                    throw new WebApplicationException("Username already exists", Response.Status.CONFLICT);
                }
            });
        }

        try {
            UserRow updated = repository.update(uuid, req.username(), passwordHash, req.name(), null)
                    .orElseThrow(() -> new NotFoundException("User not found"));
            return toResponse(updated);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) {
                throw new WebApplicationException("Username already exists", Response.Status.CONFLICT);
            }
            throw e;
        }
    }

    public void delete(UUID uuid) {
        boolean deleted = repository.softDelete(uuid, null);
        if (!deleted) {
            throw new NotFoundException("User not found");
        }
    }
}
