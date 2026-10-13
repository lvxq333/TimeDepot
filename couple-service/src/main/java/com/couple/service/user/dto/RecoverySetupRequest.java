package com.couple.service.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 老用户首次补设找回问答时提交当前密码和 1～3 道问题。 */
public record RecoverySetupRequest(
        @NotBlank String currentPassword,
        @NotNull @Size(min = 1, max = 3) List<@Valid RecoveryQuestionRequest> questions) {
}
