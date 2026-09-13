package com.couple.service.annotation.vo;

import java.util.List;

/** 一封情绪信的可见批注列表。 */
public record AnnotationListResponse(List<AnnotationView> annotations) {
}
