package id.my.agungdh.user.application.service;

import id.my.agungdh.common.application.dto.PageResponse;
import id.my.agungdh.common.infrastructure.security.Argon2Hasher;
import id.my.agungdh.user.application.dto.UserCreateRequest;
import id.my.agungdh.user.application.dto.UserResponse;
import id.my.agungdh.user.application.dto.UserUpdateRequest;
import id.my.agungdh.user.application.mapper.UserMapper;
import id.my.agungdh.user.domain.model.User;
import id.my.agungdh.user.infrastructure.persistence.UserRepository;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RunOnVirtualThread
public class UserService {

    private static final Logger LOG = Logger.getLogger(UserService.class);

    @Inject
    UserRepository repository;

    @Inject
    UserMapper mapper;

    @Transactional
    public UserResponse create(UserCreateRequest req) {
        if (repository.findByUsername(req.username()).isPresent()) {
            throw new WebApplicationException("Username already exists", Response.Status.CONFLICT);
        }
        User user = mapper.fromCreateRequest(req);
        user.password = Argon2Hasher.hash(req.password());
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
        return mapper.toResponse(user);
    }

    public UserResponse findByUuid(UUID uuid) {
        User user = repository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return mapper.toResponse(user);
    }

    // khusus untuk endpoint yang include soft-deleted
    public UserResponse findByUuidIncludingDeleted(UUID uuid) {
        User user = repository.findByUuidIncludeDeleted(uuid)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return mapper.toResponse(user);
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
                LOG.warnf("Invalid cursor format: %s", cursor);
                throw new WebApplicationException("Invalid cursor", Response.Status.BAD_REQUEST);
            }
            User cursorUser = repository.findByUuidIncludeDeleted(cursorUuid)
                    .orElseThrow(() -> {
                        LOG.warnf("Invalid cursor not found (including deleted): %s", cursor);
                        return new WebApplicationException("Invalid cursor", Response.Status.BAD_REQUEST);
                    });
            cursorId = cursorUser.id;
        }
        List<User> rows = repository.findAllIncludingDeleted(pageSize + 1, cursorId);
        boolean hasNext = rows.size() > pageSize;
        List<User> pageRows = hasNext ? rows.subList(0, pageSize) : rows;
        List<UserResponse> data = mapper.toResponses(pageRows);
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
                LOG.warnf("Invalid cursor format: %s", cursor);
                throw new WebApplicationException("Invalid cursor", Response.Status.BAD_REQUEST);
            }
            User cursorUser = repository.findByUuid(cursorUuid)
                    .orElseThrow(() -> {
                        LOG.warnf("Invalid cursor not found: %s", cursor);
                        return new WebApplicationException("Invalid cursor", Response.Status.BAD_REQUEST);
                    });
            cursorId = cursorUser.id;
        }

        List<User> rows = repository.findAllActive(pageSize + 1, cursorId);
        boolean hasNext = rows.size() > pageSize;
        List<User> pageRows = hasNext ? rows.subList(0, pageSize) : rows;

        List<UserResponse> data = mapper.toResponses(pageRows);

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
        }
        // MapStruct akan apply field non-null (username, name); password di-handle manual
        mapper.updateFromRequest(req, user);
        if (req.password() != null && !req.password().isBlank()) {
            user.password = Argon2Hasher.hash(req.password());
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
        return mapper.toResponse(user);
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
