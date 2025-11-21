package edu.ucsal.fiadopay.application.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsal.fiadopay.application.dto.MerchantWebhookDto;
import edu.ucsal.fiadopay.application.dto.PaymentStatusUpdateDto;
import edu.ucsal.fiadopay.infrastructure.security.criptography.EncodingService;
import edu.ucsal.fiadopay.domain.model.WebhookDelivery;
import edu.ucsal.fiadopay.application.dto.PaymentUpdatedEvent;
import edu.ucsal.fiadopay.domain.repository.MerchantRepository;
import edu.ucsal.fiadopay.domain.repository.WebhookDeliveryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class WebhookListener {

    private final MerchantRepository merchantRepository;
    private final WebhookDeliveryRepository deliveries;
    private final ObjectMapper objectMapper;
    private final EncodingService encodingService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Value("${fiadopay.webhook-secret}")
    String secret;

    public WebhookListener(MerchantRepository merchantRepository, WebhookDeliveryRepository deliveries, ObjectMapper objectMapper, EncodingService encodingService) {
        this.merchantRepository = merchantRepository;
        this.deliveries = deliveries;
        this.objectMapper = objectMapper;
        this.encodingService = encodingService;
    }

    @EventListener
    public void sendWebhook(PaymentUpdatedEvent PaymentEvent) {
        var p = PaymentEvent.payment();
        var merchant = merchantRepository.findById(p.getMerchantId()).orElseThrow(() -> new EntityNotFoundException("Merchant not found"));
        if (merchant == null || merchant.getWebhookUrl() == null || merchant.getWebhookUrl().isBlank()) return;

        String payload;
        try {
            var data = new PaymentStatusUpdateDto(
                    p.getId(),
                    p.getStatus().name(),
                    Instant.now().toString()
            );

            var event = new MerchantWebhookDto("evt_" + UUID.randomUUID().toString().substring(0, 8),
                    "payment.updated",
                    data);

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
            System.out.println("payload enviado: "+d.getPayload());
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

}