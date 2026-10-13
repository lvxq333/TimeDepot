package com.couple.service.user.vo;

import java.time.LocalDateTime;

public record UserUnbindResponse(String message, LocalDateTime unboundAt, String newCoupleCode) {
}
