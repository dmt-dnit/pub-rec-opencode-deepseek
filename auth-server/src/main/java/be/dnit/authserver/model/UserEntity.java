package be.dnit.authserver.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String name;

    @Enumerated(EnumType.STRING)
    private Role role = Role.CUSTOMER;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    private Instant createdAt = Instant.now();

    public enum Role { CUSTOMER, WAREHOUSE_STAFF, ADMIN }
    public enum Status { PENDING, ACTIVE, DISABLED }

    public UserEntity() {}

    public UserEntity(String email, String password, String name, Role role, Status status) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
