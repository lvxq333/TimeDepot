package com.couple.service.memo.vo;

import java.time.LocalDateTime;

/** 共同备忘事件列表项，一条记录对应一整条情绪信回复链。 */
public record MemoEventSummaryView(String threadId,
                                   Long rootLetterId,
                                   String title,
                                   Long initiatorId,
                                   LocalDateTime startedAt,
                                   LocalDateTime lastReplyAt,
                                   int letterCount,
                                   int unreadCount,
                                   String memoStatus,
                                   int pendingActionCount) {
}
