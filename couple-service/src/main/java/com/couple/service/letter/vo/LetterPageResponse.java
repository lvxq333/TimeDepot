package com.couple.service.letter.vo;

import java.util.List;

/** 情绪信分页查询响应。 */
public record LetterPageResponse(List<LetterSummaryView> records,
                                 long total,
                                 int page,
                                 int pageSize) {
}
