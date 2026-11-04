package com.veici.asset.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserEnabledForm {

    @NotNull(message = "缺少版本号")
    @PositiveOrZero(message = "版本号无效")
    private Long version;

    @NotNull(message = "请选择账号状态")
    private Boolean enabled;
}