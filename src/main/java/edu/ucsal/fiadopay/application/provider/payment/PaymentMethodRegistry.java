package edu.ucsal.fiadopay.application.provider.payment;

import edu.ucsal.fiadopay.infrastructure.annotation.PaymentMethod;
import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentMethodRegistry {

    private final Map<String, PaymentProvider> paymentProviders = new HashMap<>();
    private final ApplicationContext applicationContext;

    public PaymentMethodRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void setProcessors() {

        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(PaymentMethod.class);

        for(Object bean : beans.values()) {
            PaymentMethod paymentMethod = bean.getClass().getAnnotation(PaymentMethod.class);
            if (paymentMethod != null && bean instanceof PaymentProvider) {
                paymentProviders.put(paymentMethod.paymentType(), (PaymentProvider) bean);
            }
        }
    }

    public PaymentProvider getProvider(String method) {
        return paymentProviders.getOrDefault(method.toUpperCase(), paymentProviders.get("DEFAULT"));
    }

}
