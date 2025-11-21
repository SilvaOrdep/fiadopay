package edu.ucsal.fiadopay.application.dto;

import edu.ucsal.fiadopay.domain.model.Payment;

public record PaymentUpdatedEvent(Payment payment) {
}