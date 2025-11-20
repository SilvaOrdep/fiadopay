package edu.ucsal.fiadopay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsal.fiadopay.criptography.EncodingService;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.domain.WebhookDelivery;
import edu.ucsal.fiadopay.dto.PaymentRequest;
import edu.ucsal.fiadopay.dto.PaymentResponse;
import edu.ucsal.fiadopay.provider.payment.PaymentProvider;
import edu.ucsal.fiadopay.registry.PaymentMethodRegistry;
import edu.ucsal.fiadopay.repo.PaymentRepository;
import edu.ucsal.fiadopay.repo.WebhookDeliveryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Service
public class PaymentService {
    private final MerchantService merchantService;
    private final PaymentRepository payments;
    private final WebhookDeliveryRepository deliveries;
    private final ObjectMapper objectMapper;
    private final EncodingService encodingService;
    private final PaymentMethodRegistry paymentMethodRegistry;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Value("${fiadopay.webhook-secret}")
    String secret;
    @Value("${fiadopay.processing-delay-ms}")
    long delay;
    @Value("${fiadopay.failure-rate}")
    double failRate;

    public PaymentService(MerchantService merchantService, PaymentRepository payments, WebhookDeliveryRepository deliveries, ObjectMapper objectMapper, EncodingService encodingService, PaymentMethodRegistry paymentMethodRegistry) {
        this.merchantService = merchantService;
        this.payments = payments;
        this.deliveries = deliveries;
        this.objectMapper = objectMapper;
        this.encodingService = encodingService;
        this.paymentMethodRegistry = paymentMethodRegistry;
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

        executorService.submit( () -> processAndWebhook(payment.getId()));
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
        sendWebhook(p);
        return Map.of("id", "ref_" + UUID.randomUUID(), "status", "PENDING");
    }

    private void processAndWebhook(String paymentId) {
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

        sendWebhook(p);
    }

    private void sendWebhook(Payment p) {
        var merchant = merchantService.findMerchantById(p.getMerchantId());
        if (merchant == null || merchant.getWebhookUrl() == null || merchant.getWebhookUrl().isBlank()) return;

        String payload;
        try {
            var data = Map.of(
                    "paymentId", p.getId(),
                    "status", p.getStatus().name(),
                    "occurredAt", Instant.now().toString()
            );
            var event = Map.of(
                    "id", "evt_" + UUID.randomUUID().toString().substring(0, 8),
                    "type", "payment.updated",
                    "data", data
            );
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            // fallback mínimo: não envia webhook se falhar a serialização
            return;
        }

        String signature = encodingService.executeEncodingStrategy(payload, secret);

        var delivery = deliveries.save(WebhookDelivery.builder()
                .eventId("evt_" + UUID.randomUUID().toString().substring(0, 8))
                .eventType("payment.updated")
                .paymentId(p.getId())
                .targetUrl(merchant.getWebhookUrl())
                .signature(signature)
                .payload(payload)
                .attempts(0)
                .delivered(false)
                .lastAttemptAt(null)
                .build());
        executorService.submit(() -> tryDeliver(delivery.getId()));
    }

    private void tryDeliver(Long deliveryId) {
        var d = deliveries.findById(deliveryId).orElse(null);
        if (d == null) return;
        try {
            var client = HttpClient.newHttpClient();
            var req = HttpRequest.newBuilder(URI.create(d.getTargetUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-Event-Type", d.getEventType())
                    .header("X-Signature", d.getSignature())
                    .POST(HttpRequest.BodyPublishers.ofString(d.getPayload()))
                    .build();
            var res = client.send(req, HttpResponse.BodyHandlers.ofString());
            d.setAttempts(d.getAttempts() + 1);
            d.setLastAttemptAt(Instant.now());
            d.setDelivered(res.statusCode() >= 200 && res.statusCode() < 300);
            deliveries.save(d);
            if (!d.isDelivered() && d.getAttempts() < 5) {
                long delay = d.getAttempts() * 1000L;
                scheduleRetry(deliveryId, delay);
            }
        } catch (Exception e) {
            d.setAttempts(d.getAttempts() + 1);
            d.setLastAttemptAt(Instant.now());
            d.setDelivered(false);
            deliveries.save(d);
            if (d.getAttempts() < 5) {
                long delay = d.getAttempts() * 1000L;
                scheduleRetry(deliveryId, delay);
            }
        }
    }

    private void scheduleRetry(Long deliveryId, long delayMs) {
        scheduler.schedule(() -> tryDeliver(deliveryId), delayMs, TimeUnit.MILLISECONDS);
    }


    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getStatus().name(), p.getMethod(),
                p.getAmount(), p.getInstallments(), p.getMonthlyInterest(),
                p.getTotalWithInterest()
        );
    }
}
