package edu.ucsal.fiadopay.application.dto;

public record PaymentStatusUpdateDto(String paymentId, String status, String occurredAt) {
}
