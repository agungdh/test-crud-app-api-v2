package id.my.agungdh.repository;

import id.my.agungdh.entity.UserRow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository {

    @Inject
    DataSource dataSource;

    private UserRow mapRow(ResultSet rs) throws SQLException {
        return new UserRow(
                rs.getLong("id"),
                (UUID) rs.getObject("uuid"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getObject("created_by") != null ? rs.getLong("created_by") : null,
                rs.getTimestamp("updated_at").toInstant(),
                rs.getObject("updated_by") != null ? rs.getLong("updated_by") : null,
                rs.getTimestamp("deleted_at") != null ? rs.getTimestamp("deleted_at").toInstant() : null,
                rs.getObject("deleted_by") != null ? rs.getLong("deleted_by") : null
        );
    }

    public Optional<UserRow> findByUuid(UUID uuid) {
        String sql = """
                SELECT id, uuid, username, password, name,
                       created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
                FROM users WHERE uuid = ? AND deleted_at IS NULL
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<UserRow> findByUsername(String username) {
        String sql = """
                SELECT id, uuid, username, password, name,
                       created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
                FROM users WHERE username = ? AND deleted_at IS NULL
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public UserRow insert(String username, String passwordHash, String name, Long actorId) {
        String sql = """
                INSERT INTO users (username, password, name, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id, uuid, username, password, name,
                          created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, name);
            if (actorId != null) {
                ps.setLong(4, actorId);
                ps.setLong(5, actorId);
            } else {
                ps.setNull(4, Types.BIGINT);
                ps.setNull(5, Types.BIGINT);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                throw new RuntimeException("Failed to insert user");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<UserRow> update(UUID uuid, String username, String passwordHash, String name, Long actorId) {
        // Build dynamic SET clause
        StringBuilder sql = new StringBuilder("UPDATE users SET ");
        List<Object> params = new ArrayList<>();
        List<Integer> types = new ArrayList<>();

        if (username != null) {
            sql.append("username = ?, ");
            params.add(username);
            types.add(Types.VARCHAR);
        }
        if (passwordHash != null) {
            sql.append("password = ?, ");
            params.add(passwordHash);
            types.add(Types.VARCHAR);
        }
        if (name != null) {
            sql.append("name = ?, ");
            params.add(name);
            types.add(Types.VARCHAR);
        }
        sql.append("updated_at = now(), ");
        if (actorId != null) {
            sql.append("updated_by = ?, ");
            params.add(actorId);
            types.add(Types.BIGINT);
        } else {
            sql.append("updated_by = NULL, ");
        }
        // Remove trailing ", "
        sql.setLength(sql.length() - 2);
        sql.append(" WHERE uuid = ? AND deleted_at IS NULL RETURNING id, uuid, username, password, name, created_at, created_by, updated_at, updated_by, deleted_at, deleted_by");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            for (int i = 0; i < params.size(); i++) {
                if (types.get(i) == Types.VARCHAR) {
                    ps.setString(idx++, (String) params.get(i));
                } else if (types.get(i) == Types.BIGINT) {
                    ps.setLong(idx++, (Long) params.get(i));
                }
            }
            ps.setObject(idx, uuid);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean softDelete(UUID uuid, Long actorId) {
        String sql = """
                UPDATE users SET deleted_at = now(), deleted_by = ?, updated_at = now(), updated_by = ?
                WHERE uuid = ? AND deleted_at IS NULL
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (actorId != null) {
                ps.setLong(1, actorId);
                ps.setLong(2, actorId);
            } else {
                ps.setNull(1, Types.BIGINT);
                ps.setNull(2, Types.BIGINT);
            }
            ps.setObject(3, uuid);
            int updated = ps.executeUpdate();
            return updated > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Keyset pagination: WHERE (created_at, id) > (cursorCreatedAt, cursorId) ORDER BY created_at ASC, id ASC
     * Cursor format: Base64 of "instant_epoch_milli:id" OR we use query params directly.
     * Simpler: pass cursorCreatedAt and cursorId separately.
     */
    public List<UserRow> findAll(int limit, Instant cursorCreatedAt, Long cursorId) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, uuid, username, password, name,
                       created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
                FROM users WHERE deleted_at IS NULL
                """);
        if (cursorCreatedAt != null && cursorId != null) {
            sql.append(" AND (created_at, id) > (?, ?) ");
        }
        sql.append(" ORDER BY created_at ASC, id ASC LIMIT ?");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (cursorCreatedAt != null && cursorId != null) {
                ps.setTimestamp(idx++, Timestamp.from(cursorCreatedAt));
                ps.setLong(idx++, cursorId);
            }
            ps.setInt(idx, limit + 1); // fetch one extra to detect hasNext

            List<UserRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<UserRow> findById(long id) {
        String sql = """
                SELECT id, uuid, username, password, name,
                       created_at, created_by, updated_at, updated_by, deleted_at, deleted_by
                FROM users WHERE id = ? AND deleted_at IS NULL
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
