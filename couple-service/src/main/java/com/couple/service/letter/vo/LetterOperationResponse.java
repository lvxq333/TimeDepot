package com.couple.service.letter.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/** 创建、状态变更和删除操作的统一响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LetterOperationResponse(Long letterId,
                                      String status,
                                      Integer cooldownRemaining,
                                      LocalDateTime cooldownEndAt,
                                      LocalDateTime sentAt,
                                      LocalDateTime readAt,
                                      LocalDateTime resolvedAt,
                                      String message) {
}
