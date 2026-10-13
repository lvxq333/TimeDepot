package com.couple.service.letter.vo;

import java.util.List;
import java.util.Map;

/** 同一话题下当前用户可见的信件时间线。 */
public record LetterThreadView(String threadId,
                               String title,
                               String status,
                               List<LetterDetailView> letters,
                               Map<Long, Integer> annotationCounts) {
}
