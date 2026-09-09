package com.couple.common.exception;

public enum ErrorCode {
    INVALID_REQUEST(400001, "请求参数错误"),
    UNAUTHORIZED(401001, "未登录或登录已失效"),
    FORBIDDEN(403001, "没有权限执行此操作"),
    NOT_FOUND(404001, "资源不存在"),
    CONFLICT(409001, "资源状态冲突"),
    BUSINESS_ERROR(400999, "业务处理失败"),
    INTERNAL_ERROR(500001, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
