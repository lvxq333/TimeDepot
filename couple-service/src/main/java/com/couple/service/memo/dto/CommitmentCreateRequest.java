package com.couple.service.memo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 提出一条归属于指定参与方的承诺。 */
public record CommitmentCreateRequest(
        @NotBlank(message = "承诺内容不能为空")
        @Size(max = 2000, message = "承诺内容不能超过 2000 字") String content) {
}
