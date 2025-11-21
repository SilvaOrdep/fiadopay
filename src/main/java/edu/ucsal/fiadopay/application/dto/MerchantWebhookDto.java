package edu.ucsal.fiadopay.application.dto;

public record MerchantWebhookDto(String id, String type, PaymentStatusUpdateDto data) {
}
