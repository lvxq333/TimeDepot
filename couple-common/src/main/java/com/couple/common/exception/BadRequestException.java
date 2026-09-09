package com.couple.common.exception;

public class BadRequestException extends BusinessException {
    public BadRequestException(String message) {
        super(ErrorCode.INVALID_REQUEST, message);
    }
}
