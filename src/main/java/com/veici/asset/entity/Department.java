package com.veici.asset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "department")
public class Department extends VersionedEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    public static Department create(String name) {
        var department = new Department();
        department.rename(name);
        return department;
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("部门名称不能为空");
        }

        String normalizedName = name.strip();

        if (normalizedName.length() > 100) {
            throw new IllegalArgumentException("部门名称不能超过100个字符");
        }

        this.name = normalizedName;
    }
}