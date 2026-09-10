package com.couple.service.user.vo;

import java.time.LocalDateTime;

/** 当前用户完整资料响应。 */
public record UserProfileResponse(Long userId, String username, String nickname, String coupleCode,
                                  boolean isBound, Long partnerId,
                                  String partnerNickname, String partnerRemark,
                                  LocalDateTime boundAt, MembershipView membership,
                                  FeatureFlagsView featureFlags, LimitsView limits) {
}
