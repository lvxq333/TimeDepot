package com.couple.common.response;

import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * REST 接口统一响应体：code 为标准 HTTP 状态码，message 为提示，data 为业务数据。
 * <p>默认 success 方法生成 200，也可显式生成 201 等其他 2xx 状态；
 * failure 方法接受 4xx 或 5xx。
 * 此类仅构建 JSON 数据，不会自动设置 HTTP 响应状态；失败状态由全局处理器设置。</p>
 * @param <T> 响应数据类型，例如用户 VO、列表或分页结果，与请求 DTO 类型无关
 */
public final class ApiResponse<T> {

    private final int code;
    private final String message;
    private final T data;
    private final Instant timestamp;

    /**
     * 内部构造方法，统一初始化响应并记录生成时间。
     * @param code 标准 HTTP 状态码
     * @param message 成功提示或具体失败原因
     * @param data 响应数据，无数据时为 null
     */
    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now();
    }

    /**
     * 构建带业务数据的成功响应，code 为 200。
     * @param data 需要返回给前端的数据
     * @param <T> 响应数据类型
     * @return 统一成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    /**
     * 构建指定 2xx 状态的成功响应，用于创建资源等需要返回 201 的场景。
     * @param status 标准 2xx HTTP 状态
     * @param data 需要返回给前端的数据
     * @throws IllegalArgumentException 当状态不是 2xx 时抛出
     */
    public static <T> ApiResponse<T> success(HttpStatus status, T data) {
        if (!status.is2xxSuccessful()) {
            throw new IllegalArgumentException("成功响应必须使用 2xx 状态");
        }
        return new ApiResponse<>(status.value(), "success", data);
    }

    /** 构建无业务数据的成功响应，例如修改成功后只需返回确认结果。 */
    public static ApiResponse<Void> success() {
        return success(null);
    }

    /**
     * 使用状态码的标准英文描述构建失败响应，data 为 null。
     * @param status 4xx 或 5xx 状态
     */
    public static ApiResponse<Void> failure(HttpStatus status) {
        return failure(status, status.getReasonPhrase());
    }

    /**
     * 使用具体错误提示构建失败响应，通常由全局异常处理器调用。
     * @param status 标准 HTTP 错误状态
     * @param message 面向用户的提示，例如“用户名已存在”
     * @throws IllegalArgumentException 当状态不是 4xx 或 5xx 时抛出
     */
    public static ApiResponse<Void> failure(HttpStatus status, String message) {
        if (!status.isError()) {
            throw new IllegalArgumentException("失败响应必须使用 4xx 或 5xx 状态");
        }
        return new ApiResponse<>(status.value(), message, null);
    }

    /** 返回响应体中的标准 HTTP 状态码，供序列化和调用方读取。 */
    public int getCode() {
        return code;
    }

    /** 返回面向用户的提示信息。 */
    public String getMessage() {
        return message;
    }

    /** 返回业务数据；失败响应或无数据的成功响应中为 null。 */
    public T getData() {
        return data;
    }

    /** 返回响应创建时刻，使用 Instant 表示时间点。 */
    public Instant getTimestamp() {
        return timestamp;
    }
}
