package id.my.agungdh.repository;

import id.my.agungdh.entity.User;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements BaseRepository<User> {

    // Default exclude soft-deleted via BaseEntity @Filter("softDeleteFilter") — di-enable otomatis di SoftDeleteFilter
    // Laravel-style: repository.find("...") -> exclude (default)
    //                repository.withTrashed().find("...") atau withSoftDelete() -> include soft-deleted
    //                Contoh: repository.withTrashed().find("uuid", uuid).firstResultOptional()

    public Optional<User> findByUuid(UUID uuid) {
        enableSoftDeleteFilter();
        return find("uuid", uuid).firstResultOptional();
    }

    public Optional<User> findByUsername(String username) {
        enableSoftDeleteFilter();
        return find("username", username).firstResultOptional();
    }

    public Optional<User> findByIdAndActive(Long id) {
        enableSoftDeleteFilter();
        return find("id", id).firstResultOptional();
    }

    /**
     * Keyset pagination: (createdAt, id) > (cursorCreatedAt, cursorId) ORDER BY createdAt ASC, id ASC
     * Expanded to: createdAt > ?1 OR (createdAt = ?1 AND id > ?2)
     */
    public List<User> findAllActive(int limitPlusOne, Instant cursorCreatedAt, Long cursorId) {
        enableSoftDeleteFilter();
        if (cursorCreatedAt != null && cursorId != null) {
            return find("(createdAt > ?1 or (createdAt = ?1 and id > ?2)) order by createdAt asc, id asc", cursorCreatedAt, cursorId)
                    .page(0, limitPlusOne).list();
        } else {
            return find("order by createdAt asc, id asc")
                    .page(0, limitPlusOne).list();
        }
    }

    // Laravel withTrashed() style: include soft-deleted — usage: repository.withTrashed().find(...).list()
    public Optional<User> findByUuidIncludeDeleted(UUID uuid) {
        withTrashed();
        try {
            return find("uuid", uuid).firstResultOptional();
        } finally {
            withoutTrashed();
        }
    }

    public List<User> findAllIncludingDeleted(int limitPlusOne, Instant cursorCreatedAt, Long cursorId) {
        withTrashed();
        try {
            if (cursorCreatedAt != null && cursorId != null) {
                return find("(createdAt > ?1 or (createdAt = ?1 and id > ?2)) order by createdAt asc, id asc", cursorCreatedAt, cursorId)
                        .page(0, limitPlusOne).list();
            } else {
                return find("order by createdAt asc, id asc").page(0, limitPlusOne).list();
            }
        } finally {
            withoutTrashed();
        }
    }

    public List<User> onlyTrashed(int limitPlusOne, Instant cursorCreatedAt, Long cursorId) {
        // hanya yang soft-deleted
        disableSoftDeleteFilter();
        try {
            if (cursorCreatedAt != null && cursorId != null) {
                return find("deletedAt is not null and (createdAt > ?1 or (createdAt = ?1 and id > ?2)) order by createdAt asc, id asc", cursorCreatedAt, cursorId)
                        .page(0, limitPlusOne).list();
            } else {
                return find("deletedAt is not null order by createdAt asc, id asc").page(0, limitPlusOne).list();
            }
        } finally {
            enableSoftDeleteFilter();
        }
    }
}
