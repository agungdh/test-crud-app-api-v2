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

    public Optional<User> findByUuid(UUID uuid) {
        return find("uuid = ?1 and deletedAt is null", uuid).firstResultOptional();
    }

    public Optional<User> findByUsername(String username) {
        return find("username = ?1 and deletedAt is null", username).firstResultOptional();
    }

    public Optional<User> findByIdAndActive(Long id) {
        return find("id = ?1 and deletedAt is null", id).firstResultOptional();
    }

    /**
     * Keyset pagination: (createdAt, id) > (cursorCreatedAt, cursorId) ORDER BY createdAt ASC, id ASC
     * Expanded to: createdAt > ?1 OR (createdAt = ?1 AND id > ?2)
     */
    public List<User> findAllActive(int limitPlusOne, Instant cursorCreatedAt, Long cursorId) {
        if (cursorCreatedAt != null && cursorId != null) {
            return find("deletedAt is null and (createdAt > ?1 or (createdAt = ?1 and id > ?2)) order by createdAt asc, id asc", cursorCreatedAt, cursorId)
                    .page(0, limitPlusOne).list();
        } else {
            return find("deletedAt is null order by createdAt asc, id asc")
                    .page(0, limitPlusOne).list();
        }
    }
}
