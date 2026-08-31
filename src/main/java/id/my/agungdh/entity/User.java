package id.my.agungdh.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "uuid", nullable = false, updatable = false, unique = true)
    public UUID uuid;

    @Column(name = "username", nullable = false)
    public String username;

    @Column(name = "password", nullable = false)
    public String password;

    @Column(name = "name", nullable = false)
    public String name;

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
