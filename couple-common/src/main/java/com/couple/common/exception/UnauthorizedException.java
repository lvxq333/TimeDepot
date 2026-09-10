package com.couple.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 身份认证失败，对应 HTTP 401。
 * <p>适用场景：未携带 Token、Token 无效或过期，以及登录凭证错误。
 * 由业务代码抛出，全局异常处理器将提示信息封装为统一失败响应。</p>
 */
public class UnauthorizedException extends BusinessException {
    /** 创建使用默认提示“请先登录”的认证异常。 */
    public UnauthorizedException() {
        super(HttpStatus.UNAUTHORIZED, "请先登录");
    }

    /**
     * 创建包含具体失败原因的异常。
     * @param message 可直接展示给用户的提示，不应包含密码、密钥或内部错误详情
     */
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
