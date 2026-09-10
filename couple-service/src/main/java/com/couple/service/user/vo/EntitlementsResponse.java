package com.couple.service.user.vo;

/** 会员、功能开关、配额和使用量响应。 */
public record EntitlementsResponse(MembershipView membership,
                                   FeatureFlagsView featureFlags,
                                   LimitsView limits,
                                   UsageView usage) {
}
