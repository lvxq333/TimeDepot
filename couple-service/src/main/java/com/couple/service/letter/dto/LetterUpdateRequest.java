package com.couple.service.letter.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 修改情绪信草稿的请求，字段规则与创建草稿保持一致。 */
public record LetterUpdateRequest(
        @Size(max = 100, message = "标题不能超过 100 字") String title,
        @NotBlank(message = "信件内容不能为空")
        @Size(max = 5000, message = "信件内容不能超过 5000 字") String content,
        @NotBlank(message = "请选择情绪标签")
        @Pattern(regexp = "angry|sad|doubt|hurt|suggest", message = "情绪标签不合法") String emotionTag,
        @Min(value = 1, message = "情绪强度不能小于 1")
        @Max(value = 10, message = "情绪强度不能大于 10") Integer intensity) {
}
