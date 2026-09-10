package com.couple.service.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 当前用户修改伴侣备注的请求。 */
public record PartnerRemarkUpdateRequest(
        @NotBlank(message = "伴侣备注不能为空")
        @Size(max = 50, message = "伴侣备注不能超过 50 位")
        String partnerRemark) {
}
