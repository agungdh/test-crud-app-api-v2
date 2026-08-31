package id.my.agungdh.repository;

import id.my.agungdh.entity.BaseEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import org.hibernate.Session;

public interface BaseRepository<T extends BaseEntity> extends PanacheRepositoryBase<T, Long> {

    default void enableSoftDeleteFilter() {
        try {
            Session s = getEntityManager().unwrap(Session.class);
            if (s.getEnabledFilter("softDeleteFilter") == null) s.enableFilter("softDeleteFilter");
        } catch (Exception ignored) {}
    }

    default void disableSoftDeleteFilter() {
        try {
            Session s = getEntityManager().unwrap(Session.class);
            var f = s.getEnabledFilter("softDeleteFilter");
            if (f != null) s.disableFilter("softDeleteFilter");
        } catch (Exception ignored) {}
    }

    // Laravel-like: withTrashed() / withSoftDelete() -> include soft-deleted
    default BaseRepository<T> withTrashed() {
        disableSoftDeleteFilter();
        return this;
    }

    default BaseRepository<T> withSoftDelete() {
        return withTrashed();
    }

    default BaseRepository<T> withoutTrashed() {
        enableSoftDeleteFilter();
        return this;
    }

    default BaseRepository<T> onlyTrashed() {
        disableSoftDeleteFilter();
        return this;
    }
}
