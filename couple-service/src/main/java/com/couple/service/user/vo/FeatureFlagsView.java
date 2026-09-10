package com.couple.service.user.vo;

/** 前端功能开关响应。 */
public record FeatureFlagsView(boolean aiSuggestionEnabled,
                               boolean relationshipTimelineEnabled,
                               boolean premiumThemesEnabled) {
}
