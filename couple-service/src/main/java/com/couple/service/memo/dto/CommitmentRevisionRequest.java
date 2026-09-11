package com.couple.service.memo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 提出承诺的新版本；旧版本在对方确认前继续有效。 */
public record CommitmentRevisionRequest(
        @NotBlank(message = "承诺内容不能为空")
        @Size(max = 2000, message = "承诺内容不能超过 2000 字") String content) {
}
