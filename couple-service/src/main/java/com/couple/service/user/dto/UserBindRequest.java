package com.couple.service.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 伴侣绑定请求。 */
public record UserBindRequest(
        @NotBlank(message = "配对码不能为空")
        @Pattern(regexp = "[A-Za-z0-9]{6}", message = "配对码必须为 6 位字母或数字")
        String coupleCode) {
}
