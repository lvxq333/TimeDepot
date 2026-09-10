package com.couple.service.user.vo;

import java.time.LocalDateTime;

/** 用户资料修改成功响应。 */
public record UserProfileUpdateResponse(Long userId, String username, String nickname,
                                        LocalDateTime updatedAt) {
}
