package com.veici.asset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "asset_category")
public class AssetCategory extends VersionedEntity {

    @Setter
    @Column(nullable = false, unique = true, length = 100)
    private String name;
}