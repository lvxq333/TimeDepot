package com.couple.common.response;

import java.time.Instant;

/**
 * 所有 REST 接口统一返回结构。
 * code=200 表示成功，其余 code 表示业务或系统错误。
 */
public final class ApiResponse<T> {

    private final int code;
    private final String message;
    private final T data;
    private final Instant timestamp;

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now();
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> failure(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
