package edu.ucsal.fiadopay.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefundRequest(
    @NotBlank String paymentId
) {}
