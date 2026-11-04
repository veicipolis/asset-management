package com.veici.asset;

import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashTool {

    public static void main(String[] args) {
        String password = System.getenv("DEMO_PASSWORD");

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "请在运行配置中设置 DEMO_PASSWORD");
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "密码的 UTF-8 长度不能超过72字节");
        }

        var encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode(password);

        System.out.println("密码哈希：" + hash);
        System.out.println("匹配验证：" + encoder.matches(password, hash));
    }
}