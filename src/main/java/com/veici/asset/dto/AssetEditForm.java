package com.veici.asset.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class AssetEditForm {

    @NotNull(message = "缺少版本号，请刷新页面后重试")
    @PositiveOrZero(message = "版本号不合法")
    private Long version;

    @NotBlank(message = "请输入资产名称")
    @Size(max = 100, message = "资产名称不能超过100个字符")
    private String name;

    @NotNull(message = "请选择资产分类")
    @Positive(message = "资产分类不合法")
    private Long categoryId;

    @NotNull(message = "请选择购置日期")
    @PastOrPresent(message = "购置日期不能晚于今天")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate purchaseDate;

    @NotNull(message = "请输入资产原值")
    @DecimalMin(value = "0.00", message = "资产原值不能为负数")
    @Digits(integer = 13, fraction = 2,
            message = "资产原值最多13位整数、2位小数")
    private BigDecimal originalValue;

    @NotBlank(message = "请输入存放位置")
    @Size(max = 100, message = "存放位置不能超过100个字符")
    private String location;
}