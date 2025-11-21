package edu.ucsal.fiadopay.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank String webhookUrl
) {}
