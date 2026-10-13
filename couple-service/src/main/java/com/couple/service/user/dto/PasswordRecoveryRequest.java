package com.couple.service.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PasswordRecoveryRequest(
        @NotBlank String username,
        @NotNull @Size(min = 1, max = 3) List<@NotBlank String> answers,
        @NotBlank @Size(min = 6, max = 20) String newPassword) {
}
