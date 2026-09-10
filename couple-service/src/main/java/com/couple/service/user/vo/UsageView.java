package com.couple.service.user.vo;

/** 当前计费周期内的功能使用量。 */
public record UsageView(int lettersThisMonth, int aiSuggestionsThisMonth) {
}
