package edu.ucsal.fiadopay.application.provider.payment.impl;

import edu.ucsal.fiadopay.infrastructure.annotation.PaymentMethod;
import edu.ucsal.fiadopay.application.provider.payment.PaymentProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Classe padrão para os métodos de pagamentos que não possuem juros ou lógicas que mudam o valor total do pagamento, como ex: PIX, DEBIT e BOLETO.
 * */
@Component
@PaymentMethod(paymentType = "DEFAULT")
public class DefaultPaymentProvider implements PaymentProvider {

    @Override
    public BigDecimal calculateTotal(BigDecimal amount, int installments) {
        return amount;
    }

    @Override
    public Double interest() {
        return 0.0;
    }

}
