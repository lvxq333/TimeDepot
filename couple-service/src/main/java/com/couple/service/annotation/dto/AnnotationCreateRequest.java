package com.couple.service.annotation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建原文批注请求，quoteEnd 为不包含在引用内的结束位置。 */
public record AnnotationCreateRequest(
        @NotNull(message = "情绪信编号不能为空")
        @Min(value = 1, message = "情绪信编号不合法") Long letterId,
        @NotBlank(message = "引用原文不能为空")
        @Size(max = 500, message = "引用原文不能超过 500 字") String quoteText,
        @NotNull(message = "引用起始位置不能为空")
        @Min(value = 0, message = "引用起始位置不能小于 0") Integer quoteStart,
        @NotNull(message = "引用结束位置不能为空")
        @Min(value = 1, message = "引用结束位置不能小于 1") Integer quoteEnd,
        @NotBlank(message = "批注内容不能为空")
        @Size(max = 2000, message = "批注内容不能超过 2000 字") String content,
        @Size(max = 20, message = "表达模板类型不能超过 20 字") String templateType) {
}
