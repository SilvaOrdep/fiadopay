package edu.ucsal.fiadopay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank String webhookUrl
) {}
