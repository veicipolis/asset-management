package com.veici.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartmentForm {

    @NotBlank(message = "请输入部门名称")
    @Size(max = 100, message = "部门名称不能超过100个字符")
    private String name;

    @PositiveOrZero(message = "版本号无效")
    private Long version;
}