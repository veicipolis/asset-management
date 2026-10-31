package com.veici.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetApplicationForm {

    @NotBlank(message = "请填写领用用途")
    @Size(max = 200, message = "领用用途不能超过200个字符")
    private String purpose;
}