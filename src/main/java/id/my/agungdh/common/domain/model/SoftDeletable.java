package id.my.agungdh.common.domain.model;

import java.time.Instant;

public interface SoftDeletable {
    Instant getDeletedAt();
    void setDeletedAt(Instant deletedAt);
    Long getDeletedBy();
    void setDeletedBy(Long deletedBy);
    default boolean isDeleted() { return getDeletedAt() != null; }
}
