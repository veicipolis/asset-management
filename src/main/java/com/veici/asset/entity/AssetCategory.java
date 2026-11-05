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

    public static AssetCategory create(String name) {
        var category = new AssetCategory();
        category.rename(name);
        return category;
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("类别名称不能为空");
        }

        String normalizedName = name.strip();

        if (normalizedName.length() > 100) {
            throw new IllegalArgumentException("类别名称不能超过100个字符");
        }

        this.name = normalizedName;
    }
}