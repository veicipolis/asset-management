package com.veici.asset.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserEditForm {

    @NotNull(message = "缺少版本号")
    @PositiveOrZero(message = "版本号无效")
    private Long version;

    @NotBlank(message = "请输入姓名")
    @Size(max = 100, message = "姓名不能超过100个字符")
    private String displayName;

    @Positive(message = "部门编号无效")
    private Long departmentId;
}