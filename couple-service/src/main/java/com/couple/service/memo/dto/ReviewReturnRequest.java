package com.couple.service.memo.dto;

import jakarta.validation.constraints.Size;

/** 暂不认同时可填写的温和退回说明。 */
public record ReviewReturnRequest(
        @Size(max = 500, message = "退回说明不能超过 500 字") String reason) {
}
