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
}