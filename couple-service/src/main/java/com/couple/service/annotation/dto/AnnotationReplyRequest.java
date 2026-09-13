package com.couple.service.annotation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 回复一条顶层批注。 */
public record AnnotationReplyRequest(
        @NotBlank(message = "回复内容不能为空")
        @Size(max = 2000, message = "回复内容不能超过 2000 字") String content) {
}
