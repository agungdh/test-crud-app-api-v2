package id.my.agungdh.repository;

import id.my.agungdh.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.Session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<User, Long> {

    // Default exclude soft-deleted via BaseEntity @Filter("softDeleteFilter") — di-enable otomatis di SoftDeleteFilter (JAX-RS filter)
    // Untuk include deleted, disable filter sementara (admin). Tanpa @Filter, query Panache normal sudah exclude.

    private void enableFilter() {
        try {
            Session s = getEntityManager().unwrap(Session.class);
            if (s.getEnabledFilter("softDeleteFilter") == null) s.enableFilter("softDeleteFilter");
        } catch (Exception ignored) {}
    }

    private void disableFilter() {
        try {
            Session s = getEntityManager().unwrap(Session.class);
            var f = s.getEnabledFilter("softDeleteFilter");
            if (f != null) s.disableFilter("softDeleteFilter");
        } catch (Exception ignored) {}
    }

    public Optional<User> findByUuid(UUID uuid) {
        enableFilter();
        return find("uuid", uuid).firstResultOptional();
    }

    public Optional<User> findByUsername(String username) {
        enableFilter();
        return find("username", username).firstResultOptional();
    }

    public Optional<User> findByIdAndActive(Long id) {
        enableFilter();
        return find("id", id).firstResultOptional();
    }

    /**
     * Keyset pagination: (createdAt, id) > (cursorCreatedAt, cursorId) ORDER BY createdAt ASC, id ASC
     * Expanded to: createdAt > ?1 OR (createdAt = ?1 AND id > ?2)
     */
    public List<User> findAllActive(int limitPlusOne, Instant cursorCreatedAt, Long cursorId) {
        enableFilter();
        if (cursorCreatedAt != null && cursorId != null) {
            return find("(createdAt > ?1 or (createdAt = ?1 and id > ?2)) order by createdAt asc, id asc", cursorCreatedAt, cursorId)
                    .page(0, limitPlusOne).list();
        } else {
            return find("order by createdAt asc, id asc")
                    .page(0, limitPlusOne).list();
        }
    }

    // Untuk admin: include soft-deleted — disable filter sementara
    public Optional<User> findByUuidIncludeDeleted(UUID uuid) {
        disableFilter();
        try {
            return find("uuid", uuid).firstResultOptional();
        } finally {
            enableFilter();
        }
    }

    public List<User> findAllIncludingDeleted(int limitPlusOne, Instant cursorCreatedAt, Long cursorId) {
        disableFilter();
        try {
            if (cursorCreatedAt != null && cursorId != null) {
                return find("(createdAt > ?1 or (createdAt = ?1 and id > ?2)) order by createdAt asc, id asc", cursorCreatedAt, cursorId)
                        .page(0, limitPlusOne).list();
            } else {
                return find("order by createdAt asc, id asc").page(0, limitPlusOne).list();
            }
        } finally {
            enableFilter();
        }
    }
}
