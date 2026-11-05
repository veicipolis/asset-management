package com.veici.asset.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReturnCancelForm {

    @NotNull(message = "缺少版本号")
    @PositiveOrZero(message = "版本号无效")
    private Long version;
}