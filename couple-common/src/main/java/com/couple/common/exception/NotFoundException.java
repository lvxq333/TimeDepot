package com.couple.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 资源不存在，对应 HTTP 404。
 * <p>适用场景：查询的用户、信件或批注不存在。
 * 由业务代码抛出，全局异常处理器将提示信息封装为统一失败响应。</p>
 */
public class NotFoundException extends BusinessException {
    /**
     * 创建包含具体失败原因的异常。
     * @param message 可直接展示给用户的提示，不应包含密码、密钥或内部错误详情
     */
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
