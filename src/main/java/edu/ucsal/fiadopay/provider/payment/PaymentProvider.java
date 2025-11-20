package edu.ucsal.fiadopay.provider.payment;

import java.math.BigDecimal;

public interface PaymentProvider {

    BigDecimal calculateTotal(BigDecimal amount, int installments);
    Double interest();

}
