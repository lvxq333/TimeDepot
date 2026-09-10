package com.couple.service.user.vo;

/** 当前会员方案的功能配额。 */
public record LimitsView(int maxLettersPerMonth, int maxAiSuggestionsPerMonth) {
}
