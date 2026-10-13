package com.couple.service.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecoveryQuestionRequest(
        @NotBlank @Size(max = 100) String question,
        @NotBlank @Size(min = 8, max = 100) String answer) {
}
