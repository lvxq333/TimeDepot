package com.couple.common.security;

public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long requireUserId() {
        CurrentUser user = get();
        if (user == null) {
            throw new com.couple.common.exception.UnauthorizedException();
        }
        return user.userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
