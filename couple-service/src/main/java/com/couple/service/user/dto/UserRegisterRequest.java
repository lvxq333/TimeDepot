package com.couple.service.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.List;

/** 用户注册请求。 */
public record UserRegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "[A-Za-z0-9]{3,20}", message = "用户名必须为 3~20 位字母或数字")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 20, message = "密码长度必须为 6~20 位")
        String password,

        @Size(min = 1, max = 50, message = "昵称长度必须为 1~50 位")
        String nickname,

        @NotNull @Size(min = 1, max = 3, message = "请设置 1~3 个找回问题")
        List<@Valid RecoveryQuestionRequest> recoveryQuestions) {
}
