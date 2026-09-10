package com.couple.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 访问权限不足，对应 HTTP 403。
 * <p>适用场景：用户已通过身份认证，但尝试修改他人的信件。
 * 由业务代码抛出，全局异常处理器将提示信息封装为统一失败响应。</p>
 */
public class ForbiddenException extends BusinessException {
    /**
     * 创建包含具体失败原因的异常。
     * @param message 可直接展示给用户的提示，不应包含密码、密钥或内部错误详情
     */
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
