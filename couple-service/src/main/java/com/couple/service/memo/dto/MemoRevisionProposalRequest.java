package com.couple.service.memo.dto;

import jakarta.validation.constraints.Size;

/** 对方针对已提交备忘录提出的完整调整版本。 */
public record MemoRevisionProposalRequest(
        @Size(max = 100, message = "事件标题不能超过 100 字") String title,
        @Size(max = 5000, message = "问题归纳不能超过 5000 字") String rootCause,
        @Size(max = 5000, message = "共同结论不能超过 5000 字") String commonGround,
        @Size(max = 100, message = "和解暗号不能超过 100 字") String secretCode) {
}
