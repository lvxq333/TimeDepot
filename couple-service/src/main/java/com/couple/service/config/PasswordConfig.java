package com.couple.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 配置密码哈希组件，业务层只依赖统一的 PasswordEncoder 接口。 */
@Configuration
public class PasswordConfig {

    /** 使用 BCrypt 保存和校验用户密码。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
