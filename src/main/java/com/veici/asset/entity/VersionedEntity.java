package com.veici.asset.entity;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class VersionedEntity extends BaseEntity {

    @Version
    private Long version;
}