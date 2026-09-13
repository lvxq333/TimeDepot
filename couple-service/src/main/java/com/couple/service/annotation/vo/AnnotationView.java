package com.couple.service.annotation.vo;

import java.time.LocalDateTime;

/** 前端渲染原文高亮和批注卡片所需的完整信息。 */
public record AnnotationView(Long annotationId,
                             Long letterId,
                             Long authorId,
                             String authorNickname,
                             String quoteText,
                             Integer quoteStart,
                             Integer quoteEnd,
                             String content,
                             String templateType,
                             LocalDateTime createdAt) {
}
