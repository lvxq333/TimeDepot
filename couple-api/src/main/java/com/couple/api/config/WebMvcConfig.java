package com.couple.api.config;

import com.couple.api.interceptor.JwtAuthInterceptor;
import com.couple.service.auth.service.AuthSessionService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册全局 MVC 拦截器，并启用 JWT 配置属性绑定。 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    /** 从配置文件读取 JWT 参数，传给认证拦截器。 */
    private final JwtProperties jwtProperties;
    private final AuthSessionService authSessionService;

    /** 创建 MVC 配置并注入 JWT 配置。 */
    public WebMvcConfig(JwtProperties jwtProperties, AuthSessionService authSessionService) {
        this.jwtProperties = jwtProperties;
        this.authSessionService = authSessionService;
    }

    /** 为受保护接口注册认证拦截器，排除静态错误页等无需认证的路径。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JwtAuthInterceptor(jwtProperties, authSessionService))
                .addPathPatterns("/api/**", "/user/**", "/letter/**", "/annotation/**", "/memo/**")
                .excludePathPatterns("/error", "/favicon.ico");
    }
}
