package com.couple.service.letter.vo;

import java.time.LocalDateTime;

/** 信箱中的事件摘要，不携带整条信件正文。 */
public record LetterThreadSummaryView(String threadId,
                                      String title,
                                      String status,
                                      int letterCount,
                                      int unreadCount,
                                      int annotationCount,
                                      LocalDateTime lastActivityAt,
                                      LetterSummaryView root,
                                      LetterSummaryView latest,
                                      Long matchedLetterId,
                                      String matchSnippet) {
}
