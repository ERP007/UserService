package com.fallguys.userservice.infrastructure.persistence.tenancy;

import com.fallguys.userservice.domain.Tenancy;
import com.fallguys.userservice.domain.TenancyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "tenancies",
        indexes = @Index(name = "idx_tenancies_type", columnList = "type")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class TenancyEntity {

    @Id
    @Column(name = "tenancy_code", nullable = false, length = 30)
    private String tenancyCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TenancyType type;

    public static TenancyEntity create(String tenancyCode, String name, TenancyType type) {
        return new TenancyEntity(tenancyCode, name, type);
    }

    public Tenancy toDomain() {
        return new Tenancy(tenancyCode, name, type);
    }

    public void updateSeedData(String name, TenancyType type) {
        this.name = name;
        this.type = type;
    }
}
