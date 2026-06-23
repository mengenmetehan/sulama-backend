package com.sulama.model.dto;

import jakarta.validation.constraints.NotBlank;

public record FcmTokenRequest(@NotBlank String token) {}
