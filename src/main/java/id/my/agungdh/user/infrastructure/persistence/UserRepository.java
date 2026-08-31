package id.my.agungdh.user.infrastructure.persistence;

import id.my.agungdh.common.infrastructure.persistence.BaseRepository;
import id.my.agungdh.user.domain.model.User;
import jakarta.enterprise.context.ApplicationScoped;

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
     * Keyset pagination: ORDER BY id DESC, cursor = UUID -> resolve to id, then id < cursorId
     */
    public List<User> findAllActive(int limitPlusOne, Long cursorId) {
        enableSoftDeleteFilter();
        if (cursorId != null) {
            return find("id < ?1 order by id desc", cursorId)
                    .page(0, limitPlusOne).list();
        } else {
            return find("order by id desc")
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

    public List<User> findAllIncludingDeleted(int limitPlusOne, Long cursorId) {
        withTrashed();
        try {
            if (cursorId != null) {
                return find("id < ?1 order by id desc", cursorId)
                        .page(0, limitPlusOne).list();
            } else {
                return find("order by id desc").page(0, limitPlusOne).list();
            }
        } finally {
            withoutTrashed();
        }
    }

    public List<User> onlyTrashed(int limitPlusOne, Long cursorId) {
        // hanya yang soft-deleted
        disableSoftDeleteFilter();
        try {
            if (cursorId != null) {
                return find("deletedAt is not null and id < ?1 order by id desc", cursorId)
                        .page(0, limitPlusOne).list();
            } else {
                return find("deletedAt is not null order by id desc").page(0, limitPlusOne).list();
            }
        } finally {
            enableSoftDeleteFilter();
        }
    }
}
