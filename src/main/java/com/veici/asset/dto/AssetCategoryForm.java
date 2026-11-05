package com.veici.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetCategoryForm {

    @NotBlank(message = "请输入类别名称")
    @Size(max = 100, message = "类别名称不能超过100个字符")
    private String name;

    @PositiveOrZero(message = "版本号无效")
    private Long version;
}