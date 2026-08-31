package id.my.agungdh.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
@SQLRestriction("deleted_at IS NULL")
public abstract class BaseEntity extends PanacheEntityBase implements SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "uuid", nullable = false, updatable = false, unique = true)
    public UUID uuid;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "created_by")
    public Long createdBy;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "updated_by")
    public Long updatedBy;

    @Column(name = "deleted_at")
    public Instant deletedAt;

    @Column(name = "deleted_by")
    public Long deletedBy;

    @Override public Instant getDeletedAt() { return deletedAt; }
    @Override public void setDeletedAt(Instant v) { this.deletedAt = v; }
    @Override public Long getDeletedBy() { return deletedBy; }
    @Override public void setDeletedBy(Long v) { this.deletedBy = v; }

    @PrePersist
    public void prePersist() {
        if (uuid == null) uuid = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
