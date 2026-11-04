package com.veici.asset.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetIssueForm {

    @NotNull(message = "缺少申请版本号，请刷新页面")
    @PositiveOrZero(message = "申请版本号不合法")
    private Long applicationVersion;

    @NotNull(message = "缺少资产版本号，请刷新页面")
    @PositiveOrZero(message = "资产版本号不合法")
    private Long assetVersion;
}