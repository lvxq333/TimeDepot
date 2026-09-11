package com.couple.api.interceptor;

import com.couple.api.annotation.NoAuth;
import com.couple.api.config.JwtProperties;
import com.couple.common.security.CurrentUser;
import com.couple.common.security.JwtTokenUtil;
import com.couple.common.security.UserContext;
import com.couple.service.auth.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** 在进入控制器前校验 Bearer JWT，并将用户身份放入请求线程上下文。 */
public class JwtAuthInterceptor implements HandlerInterceptor {

    /** JWT 密钥等认证配置。 */
    private final JwtProperties jwtProperties;
    private final AuthSessionService authSessionService;

    /** 创建使用指定 JWT 配置的认证拦截器。 */
    public JwtAuthInterceptor(JwtProperties jwtProperties, AuthSessionService authSessionService) {
        this.jwtProperties = jwtProperties;
        this.authSessionService = authSessionService;
    }

    /**
     * 放行标记了 {@link NoAuth} 的接口；其他接口必须携带有效 Bearer Token。
     * @return 认证通过或接口无需认证时返回 {@code true}
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method
                && (method.hasMethodAnnotation(NoAuth.class)
                || method.getBeanType().isAnnotationPresent(NoAuth.class))) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new com.couple.common.exception.UnauthorizedException();
        }

        CurrentUser user = JwtTokenUtil.parse(jwtProperties.getSecret(), authorization.substring(7).trim());
        authSessionService.requireActiveSession(user);
        UserContext.set(user);
        return true;
    }

    /** 请求结束后清理线程上下文，防止容器线程复用造成身份泄漏。 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
