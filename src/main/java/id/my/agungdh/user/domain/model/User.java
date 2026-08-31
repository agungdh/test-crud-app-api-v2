package id.my.agungdh.user.domain.model;

import id.my.agungdh.common.domain.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "username", nullable = false)
    public String username;

    @Column(name = "password", nullable = false)
    public String password;

    @Column(name = "name", nullable = false)
    public String name;
}
