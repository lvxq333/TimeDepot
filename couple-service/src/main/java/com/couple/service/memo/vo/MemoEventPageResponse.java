package com.couple.service.memo.vo;

import java.util.List;

/** 共同备忘事件分页结果。 */
public record MemoEventPageResponse(List<MemoEventSummaryView> records,
                                    long total,
                                    int page,
                                    int pageSize) {
}
