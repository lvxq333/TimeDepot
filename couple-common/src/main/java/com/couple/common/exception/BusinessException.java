package com.couple.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 自定义业务异常的基类，继承 RuntimeException，调用方无需声明 throws。
 * <p>保存标准 HTTP 错误状态和面向用户的提示，由全局异常处理器转换为响应。
 * 常见场景使用具体子类；没有对应子类时也可以直接创建此异常。</p>
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    /**
     * 使用 HTTP 状态的标准英文描述创建异常。
     * @param status 4xx 或 5xx 状态；需要中文提示时使用另一个构造方法
     */
    public BusinessException(HttpStatus status) {
        this(status, status.getReasonPhrase());
    }

    /**
     * 使用自定义提示创建异常。
     * @param status 4xx 或 5xx 状态，例如 429、501、503
     * @param message 可直接展示给用户的具体失败原因
     * @throws IllegalArgumentException 当状态不是 4xx 或 5xx 时抛出，表示调用代码有误
     */
    public BusinessException(HttpStatus status, String message) {
        super(message);
        if (!status.isError()) {
            throw new IllegalArgumentException("业务异常必须使用 4xx 或 5xx 状态");
        }
        this.status = status;
    }

    /** 返回用于设置实际 HTTP 响应状态和响应体 code 的状态枚举。 */
    public HttpStatus getStatus() {
        return status;
    }
}
