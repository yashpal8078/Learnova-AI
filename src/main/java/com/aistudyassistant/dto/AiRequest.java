package com.aistudyassistant.dto;

import jakarta.validation.constraints.NotBlank;

public record AiRequest(
        @NotBlank(message = "Prompt cannot be empty")
        String prompt
) {}