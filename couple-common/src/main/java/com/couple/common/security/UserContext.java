package com.couple.common.security;

/**
 * 保存当前请求已认证用户的线程上下文。
 * <p>认证拦截器在请求开始时写入，业务代码可读取当前用户；请求完成后必须清理，
 * 避免线程池复用时发生用户信息串用。</p>
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    /** 不允许实例化工具类。 */
    private UserContext() {
    }

    /** 将当前请求的认证用户绑定到线程。 */
    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    /** 返回当前线程绑定的用户；未认证时返回 {@code null}。 */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 返回当前认证用户的完整身份和会话信息。 */
    public static CurrentUser requireUser() {
        CurrentUser user = get();
        if (user == null) {
            throw new com.couple.common.exception.UnauthorizedException();
        }
        return user;
    }

    /**
     * 返回当前用户 ID。
     * @throws com.couple.common.exception.UnauthorizedException 当前请求尚未通过认证
     */
    public static Long requireUserId() {
        return requireUser().userId();
    }

    /** 清除当前线程中的用户信息，通常由拦截器的请求完成回调调用。 */
    public static void clear() {
        HOLDER.remove();
    }
}
