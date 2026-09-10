package com.couple.service.user.vo;

/** 登录成功响应，包含 JWT、伴侣关系和默认权益。 */
public record UserLoginResponse(Long userId, String username, String nickname, String coupleCode,
                                String token, long tokenExpireIn,
                                boolean isBound, Long partnerId,
                                String partnerNickname, String partnerRemark,
                                MembershipView membership, FeatureFlagsView featureFlags,
                                LimitsView limits) {
}
