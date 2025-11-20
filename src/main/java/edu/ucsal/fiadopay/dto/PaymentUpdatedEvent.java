package edu.ucsal.fiadopay.dto;

import edu.ucsal.fiadopay.domain.Payment;

public record PaymentUpdatedEvent(Payment payment) {
}