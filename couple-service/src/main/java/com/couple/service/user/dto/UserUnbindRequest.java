package com.couple.service.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/** 伴侣解绑请求；Phase 1 仅保留接口契约。 */
public record UserUnbindRequest(
        @AssertTrue(message = "必须确认解绑操作") boolean confirm,
        @Size(max = 200, message = "解绑原因不能超过 200 位") String reason) {
}
