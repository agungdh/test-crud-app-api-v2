package id.my.agungdh.service;

import id.my.agungdh.dto.PageResponse;
import id.my.agungdh.dto.UserCreateRequest;
import id.my.agungdh.dto.UserResponse;
import id.my.agungdh.dto.UserUpdateRequest;
import id.my.agungdh.entity.User;
import id.my.agungdh.repository.UserRepository;
import id.my.agungdh.util.Argon2Hasher;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RunOnVirtualThread
public class UserService {

    @Inject
    UserRepository repository;

    private UserResponse toResponse(User u) {
        return new UserResponse(u.uuid, u.username, u.name);
    }

    @Transactional
    public UserResponse create(UserCreateRequest req) {
        if (repository.findByUsername(req.username()).isPresent()) {
            throw new WebApplicationException("Username already exists", Response.Status.CONFLICT);
        }
        String hash = Argon2Hasher.hash(req.password());
        User user = new User();
        user.username = req.username();
        user.password = hash;
        user.name = req.name();
        // uuid and timestamps handled by @PrePersist
        try {
            repository.persist(user);
            // flush to get generated id/uuid? uuid already set, id after persist
            repository.flush();
        } catch (RuntimeException e) {
            Throwable cause = findConstraintViolation(e);
            if (cause != null && cause.getMessage() != null && cause.getMessage().contains("ux_users_username_active")) {
                throw new WebApplicationException("Username already exists", Response.Status.CONFLICT);
            }
            if (e.getMessage() != null && e.getMessage().contains("ux_users_username_active")) {
                throw new WebApplicationException("Username already exists", Response.Status.CONFLICT);
            }
            throw e;
        }
        return toResponse(user);
    }

    public UserResponse findByUuid(UUID uuid) {
        User user = repository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toResponse(user);
    }

    // khusus untuk endpoint yang include soft-deleted
    public UserResponse findByUuidIncludingDeleted(UUID uuid) {
        User user = repository.findByUuidIncludeDeleted(uuid)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toResponse(user);
    }

    // khusus get all include soft-deleted — tanpa param boolean
    // Cursor = UUID, order by id DESC. Resolve UUID -> id, lalu query id < cursorId
    public PageResponse<UserResponse> listIncludingDeleted(int limit, String cursor) {
        int pageSize = Math.min(Math.max(limit, 1), 100);
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            UUID cursorUuid;
            try {
                cursorUuid = UUID.fromString(cursor);
            } catch (IllegalArgumentException ex) {
                throw new WebApplicationException("Invalid cursor", Response.Status.BAD_REQUEST);
            }
            User cursorUser = repository.findByUuidIncludeDeleted(cursorUuid)
                    .orElseThrow(() -> new WebApplicationException("Invalid cursor", Response.Status.BAD_REQUEST));
            cursorId = cursorUser.id;
        }
        List<User> rows = repository.findAllIncludingDeleted(pageSize + 1, cursorId);
        boolean hasNext = rows.size() > pageSize;
        List<User> pageRows = hasNext ? rows.subList(0, pageSize) : rows;
        List<UserResponse> data = pageRows.stream().map(this::toResponse).toList();
        String nextCursor = null;
        if (hasNext) {
            User last = pageRows.get(pageRows.size() - 1);
            nextCursor = last.uuid.toString();
        }
        return new PageResponse<>(data, nextCursor, hasNext);
    }

    public PageResponse<UserResponse> list(int limit, String cursor) {
        int pageSize = Math.min(Math.max(limit, 1), 100);
        Long cursorId = null;

        if (cursor != null && !cursor.isBlank()) {
            UUID cursorUuid;
            try {
                cursorUuid = UUID.fromString(cursor);
            } catch (IllegalArgumentException ex) {
                throw new WebApplicationException("Invalid cursor", Response.Status.BAD_REQUEST);
            }
            User cursorUser = repository.findByUuid(cursorUuid)
                    .orElseThrow(() -> new WebApplicationException("Invalid cursor", Response.Status.BAD_REQUEST));
            cursorId = cursorUser.id;
        }

        List<User> rows = repository.findAllActive(pageSize + 1, cursorId);
        boolean hasNext = rows.size() > pageSize;
        List<User> pageRows = hasNext ? rows.subList(0, pageSize) : rows;

        List<UserResponse> data = pageRows.stream().map(this::toResponse).toList();

        String nextCursor = null;
        if (hasNext) {
            User last = pageRows.get(pageRows.size() - 1);
            nextCursor = last.uuid.toString();
        }

        return new PageResponse<>(data, nextCursor, hasNext);
    }

    @Transactional
    public UserResponse update(UUID uuid, UserUpdateRequest req) {
        User user = repository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (req.username() != null) {
            repository.findByUsername(req.username()).ifPresent(existing -> {
                if (!existing.uuid.equals(uuid)) {
                    throw new WebApplicationException("Username already exists", Response.Status.CONFLICT);
                }
            });
            user.username = req.username();
        }
        if (req.password() != null && !req.password().isBlank()) {
            user.password = Argon2Hasher.hash(req.password());
        }
        if (req.name() != null) {
            user.name = req.name();
        }
        // updatedAt handled by @PreUpdate, but force if needed
        try {
            repository.persist(user);
            repository.flush();
        } catch (RuntimeException e) {
            Throwable cause = findConstraintViolation(e);
            if (cause != null && cause.getMessage() != null && cause.getMessage().contains("ux_users_username_active")) {
                throw new WebApplicationException("Username already exists", Response.Status.CONFLICT);
            }
            throw e;
        }
        return toResponse(user);
    }

    @Transactional
    public void delete(UUID uuid) {
        User user = repository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.deletedAt = Instant.now();
        user.updatedAt = Instant.now();
        repository.persist(user);
        repository.flush();
    }

    private Throwable findConstraintViolation(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur.getClass().getSimpleName().contains("ConstraintViolation") || cur.getMessage() != null && cur.getMessage().contains("ux_users_username_active")) {
                return cur;
            }
            cur = cur.getCause();
        }
        return null;
    }
}
