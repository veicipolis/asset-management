package com.veici.asset.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;

@Getter
@Setter
public class UserCreateForm {

    @NotBlank(message = "请输入用户名")
    @Size(min = 3, max = 50, message = "用户名长度应为3至50个字符")
    @Pattern(
            regexp = "[a-zA-Z0-9_.-]+",
            message = "用户名只能包含英文字母、数字、下划线、点和短横线"
    )
    private String username;

    @NotBlank(message = "请输入姓名")
    @Size(max = 100, message = "姓名不能超过100个字符")
    private String displayName;

    @Positive(message = "部门编号无效")
    private Long departmentId;

    @NotBlank(message = "请输入初始密码")
    @Size(min = 12, max = 72, message = "密码长度应为12至72个字符")
    private String password;

    @NotBlank(message = "请再次输入初始密码")
    private String confirmPassword;

    @AssertTrue(message = "两次输入的密码不一致")
    public boolean isPasswordMatching() {
        return password == null || password.equals(confirmPassword);
    }

    @AssertTrue(message = "密码的UTF-8编码长度不能超过72字节")
    public boolean isPasswordWithinByteLimit() {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}