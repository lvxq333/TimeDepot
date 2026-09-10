package com.couple.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 请求参数错误，对应 HTTP 400。
 * <p>适用场景：参数值不符合业务要求，例如情绪强度超出允许范围。
 * 由业务代码抛出，全局异常处理器将提示信息封装为统一失败响应。</p>
 */
public class BadRequestException extends BusinessException {
    /**
     * 创建包含具体失败原因的异常。
     * @param message 可直接展示给用户的提示，不应包含密码、密钥或内部错误详情
     */
    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
