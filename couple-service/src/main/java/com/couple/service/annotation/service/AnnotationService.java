package com.couple.service.annotation.service;

import com.couple.service.annotation.dto.AnnotationCreateRequest;
import com.couple.service.annotation.vo.AnnotationDeleteResponse;
import com.couple.service.annotation.vo.AnnotationListResponse;
import com.couple.service.annotation.vo.AnnotationView;

/** 情绪信原文批注用例。 */
public interface AnnotationService {
    AnnotationView create(Long userId, AnnotationCreateRequest request);
    AnnotationListResponse list(Long userId, Long letterId);
    AnnotationDeleteResponse delete(Long userId, Long annotationId);
}
