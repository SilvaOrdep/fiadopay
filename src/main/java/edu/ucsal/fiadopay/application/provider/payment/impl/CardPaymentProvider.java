package edu.ucsal.fiadopay.application.provider.payment.impl;

import edu.ucsal.fiadopay.infrastructure.annotation.PaymentMethod;
import edu.ucsal.fiadopay.application.provider.payment.PaymentProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@PaymentMethod(paymentType = "CARD")
public class CardPaymentProvider implements PaymentProvider {

    @Override
    public BigDecimal calculateTotal(BigDecimal amount, int installments) {
        var base = new BigDecimal("1.01");
        var factor = base.pow(installments);
        return amount.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public Double interest() {
        return 1.0;
    }

}
