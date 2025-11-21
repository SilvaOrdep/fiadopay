package edu.ucsal.fiadopay.application.service;

import edu.ucsal.fiadopay.domain.model.Payment;
import edu.ucsal.fiadopay.application.dto.request.PaymentRequest;
import edu.ucsal.fiadopay.application.dto.response.PaymentResponse;
import edu.ucsal.fiadopay.application.dto.PaymentUpdatedEvent;
import edu.ucsal.fiadopay.application.provider.payment.PaymentProvider;
import edu.ucsal.fiadopay.application.provider.payment.PaymentMethodRegistry;
import edu.ucsal.fiadopay.domain.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PaymentService {

    private final MerchantService merchantService;
    private final PaymentRepository payments;
    private final PaymentMethodRegistry paymentMethodRegistry;

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    private final ApplicationEventPublisher events;

    @Value("${fiadopay.processing-delay-ms}")
    long delay;
    @Value("${fiadopay.failure-rate}")
    double failRate;

    public PaymentService(MerchantService merchantService, PaymentRepository payments, PaymentMethodRegistry paymentMethodRegistry, ApplicationEventPublisher events) {
        this.merchantService = merchantService;
        this.payments = payments;
        this.paymentMethodRegistry = paymentMethodRegistry;
        this.events = events;
    }

    @Transactional
    public PaymentResponse createPayment(String auth, String idemKey, PaymentRequest req) {
        var merchant = merchantService.merchantFromAuth(auth);
        var mid = merchant.getId();

        if (idemKey != null) {
            var existing = payments.findByIdempotencyKeyAndMerchantId(idemKey, mid);
            if (existing.isPresent()) return toResponse(existing.get());
        }

        PaymentProvider paymentProvider = paymentMethodRegistry.getProvider(req.method());
        Double interest = paymentProvider.interest();
        BigDecimal total = paymentProvider.calculateTotal(req.amount(), req.installments());

        var payment = Payment.builder()
                .id("pay_" + UUID.randomUUID().toString().substring(0, 8))
                .merchantId(mid)
                .method(req.method().toUpperCase())
                .amount(req.amount())
                .currency(req.currency())
                .installments(req.installments())
                .monthlyInterest(interest)
                .totalWithInterest(total)
                .status(Payment.Status.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .idempotencyKey(idemKey)
                .metadataOrderId(req.metadataOrderId())
                .build();

        payments.save(payment);

        executorService.submit( () -> processAndPublish(payment.getId()));
        return toResponse(payment);
    }

    public PaymentResponse getPayment(String id) {
        return toResponse(payments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Map<String, Object> refund(String auth, String paymentId) {
        var merchant = merchantService.merchantFromAuth(auth);
        var p = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!merchant.getId().equals(p.getMerchantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        p.setStatus(Payment.Status.REFUNDED);
        p.setUpdatedAt(Instant.now());
        payments.save(p);

        events.publishEvent(new PaymentUpdatedEvent(p));

        return Map.of("id", "ref_" + UUID.randomUUID(), "status", "PENDING");
    }

    private void processAndPublish(String paymentId) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ignored) {
        }
        var p = payments.findById(paymentId).orElse(null);
        if (p == null) return;

        var approved = Math.random() > failRate;
        p.setStatus(approved ? Payment.Status.APPROVED : Payment.Status.DECLINED);
        p.setUpdatedAt(Instant.now());
        payments.save(p);

        events.publishEvent(new PaymentUpdatedEvent(p));
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getStatus().name(), p.getMethod(),
                p.getAmount(), p.getInstallments(), p.getMonthlyInterest(),
                p.getTotalWithInterest()
        );
    }

}