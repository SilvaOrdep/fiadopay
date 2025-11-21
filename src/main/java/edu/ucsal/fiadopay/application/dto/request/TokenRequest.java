package edu.ucsal.fiadopay.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(
    @NotBlank String client_id,
    @NotBlank String client_secret
) {}
