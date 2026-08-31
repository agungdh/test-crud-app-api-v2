package id.my.agungdh.repository;

import id.my.agungdh.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<User, Long> {

    // Soft-delete otomatis di-filter via BaseEntity @SQLRestriction("deleted_at IS NULL")
    // Jadi tiap query tidak perlu tambah 'deletedAt is null' manual — reusable untuk semua entity yang extends BaseEntity
    public Optional<User> findByUuid(UUID uuid) {
        return find("uuid", uuid).firstResultOptional();
    }

    public Optional<User> findByUsername(String username) {
        return find("username", username).firstResultOptional();
    }

    public Optional<User> findByIdAndActive(Long id) {
        return find("id", id).firstResultOptional();
    }

    /**
     * Keyset pagination: (createdAt, id) > (cursorCreatedAt, cursorId) ORDER BY createdAt ASC, id ASC
     * Expanded to: createdAt > ?1 OR (createdAt = ?1 AND id > ?2)
     */
    public List<User> findAllActive(int limitPlusOne, Instant cursorCreatedAt, Long cursorId) {
        if (cursorCreatedAt != null && cursorId != null) {
            return find("(createdAt > ?1 or (createdAt = ?1 and id > ?2)) order by createdAt asc, id asc", cursorCreatedAt, cursorId)
                    .page(0, limitPlusOne).list();
        } else {
            return find("order by createdAt asc, id asc")
                    .page(0, limitPlusOne).list();
        }
    }

    // Untuk admin/restore: bypass soft-delete via native query (karena @SQLRestriction selalu aktif)
    public Optional<User> findByUuidIncludeDeleted(UUID uuid) {
        return getEntityManager()
                .createNativeQuery("SELECT * FROM users WHERE uuid = :uuid", User.class)
                .setParameter("uuid", uuid)
                .getResultStream().findFirst();
    }
}
