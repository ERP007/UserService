package com.fallguys.userservice.infrastructure.persistence.user;

import java.time.LocalDateTime;

import com.fallguys.userservice.domain.User;
import com.fallguys.userservice.domain.UserRole;
import com.fallguys.userservice.domain.UserStatus;
import com.fallguys.userservice.domain.UserTenancy;
import com.fallguys.userservice.infrastructure.persistence.tenancy.TenancyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_keycloak_id", columnNames = "keycloak_id"),
        indexes = @Index(name = "idx_users_keycloak_id", columnList = "keycloak_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_id", nullable = false, unique = true, length = 100)
    private String keycloakId;

    @Column(name = "employee_number", nullable = false, length = 100)
    private String employeeNumber;

    private String email;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private UserRole role;

    @Column(name = "tenancy_code", nullable = false, length = 30)
    private String tenancyCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "tenancy_code",
            referencedColumnName = "tenancy_code",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_users_tenancy")
    )
    private TenancyEntity tenancyEntity;

    @Column(length = 50)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private UserTenancy tenancy;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private UserEntity(
            String keycloakId,
            String employeeNumber,
            String email,
            String displayName,
            String tenancyCode,
            String position,
            UserRole role,
            UserTenancy tenancy
    ) {
        this.keycloakId = keycloakId;
        this.employeeNumber = employeeNumber;
        this.email = email;
        this.name = displayName;
        this.tenancyCode = tenancyCode;
        this.position = position;
        this.role = role;
        this.tenancy = tenancy;
        this.status = UserStatus.ACTIVE;
    }

    public static UserEntity from(User user) {
        UserEntity entity = new UserEntity(
                user.getKeycloakId(),
                user.getEmployeeNumber(),
                user.getEmail(),
                user.getDisplayName(),
                user.getTenancyCode(),
                user.getPosition(),
                user.getRole(),
                user.getTenancy()
        );
        entity.status = user.getStatus();
        return entity;
    }

    public User toDomain() {
        return User.restore(
                id,
                keycloakId,
                employeeNumber,
                email,
                name,
                tenancyCode,
                position,
                role,
                tenancy,
                status
        );
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
