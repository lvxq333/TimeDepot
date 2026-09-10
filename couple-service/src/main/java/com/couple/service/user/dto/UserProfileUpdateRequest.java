package com.couple.service.user.dto;

import jakarta.validation.constraints.Size;

/** 用户资料修改请求；当前版本只允许修改昵称。 */
public record UserProfileUpdateRequest(
        @Size(min = 1, max = 50, message = "昵称长度必须为 1~50 位") String nickname) {
}
