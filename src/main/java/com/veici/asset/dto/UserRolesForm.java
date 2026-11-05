package com.veici.asset.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class UserRolesForm {

    @NotNull(message = "缺少版本号")
    @PositiveOrZero(message = "版本号无效")
    private Long version;

    @NotNull(message = "角色列表不能为空对象")
    private Set<@NotNull @Positive Long> roleIds = new HashSet<>();
}