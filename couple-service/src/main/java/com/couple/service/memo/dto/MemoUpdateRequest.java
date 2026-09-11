package com.couple.service.memo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 编辑备忘录草稿；expectedVersion 用于防止两个标签页互相覆盖。 */
public record MemoUpdateRequest(
        @NotNull(message = "缺少当前版本号") @Min(value = 1, message = "版本号不合法") Integer expectedVersion,
        @Size(max = 100, message = "事件标题不能超过 100 字") String title,
        @Size(max = 5000, message = "问题归纳不能超过 5000 字") String rootCause,
        @Size(max = 5000, message = "共同结论不能超过 5000 字") String commonGround,
        @Size(max = 100, message = "和解暗号不能超过 100 字") String secretCode) {
}
