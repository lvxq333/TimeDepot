package com.couple.service.memo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 为整个情绪信话题创建共识备忘录草稿。 */
public record MemoCreateRequest(
        @NotNull(message = "请选择情绪信话题") Long letterId,
        @Size(max = 100, message = "事件标题不能超过 100 字") String title,
        @Size(max = 5000, message = "问题归纳不能超过 5000 字") String rootCause,
        @Size(max = 5000, message = "共同结论不能超过 5000 字") String commonGround,
        @Size(max = 100, message = "和解暗号不能超过 100 字") String secretCode) {
}
