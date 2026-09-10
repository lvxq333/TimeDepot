package com.couple.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 当前资源状态与操作冲突，对应 HTTP 409。
 * <p>适用场景：用户名已存在、已经绑定伴侣，或者冷却期尚未结束。
 * 由业务代码抛出，全局异常处理器将提示信息封装为统一失败响应。</p>
 */
public class ConflictException extends BusinessException {
    /**
     * 创建包含具体失败原因的异常。
     * @param message 可直接展示给用户的提示，不应包含密码、密钥或内部错误详情
     */
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
