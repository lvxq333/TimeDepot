package com.couple.service.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record AccountDeletionRequest(
        @NotBlank(message = "请输入当前密码") String password,
        @AssertTrue(message = "必须确认注销账号") boolean confirm) { }
