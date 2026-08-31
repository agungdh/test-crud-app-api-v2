package id.my.agungdh.entity;

import java.time.Instant;

public interface SoftDeletable {
    Instant getDeletedAt();
    void setDeletedAt(Instant deletedAt);
    Long getDeletedBy();
    void setDeletedBy(Long deletedBy);
    default boolean isDeleted() { return getDeletedAt() != null; }
}
