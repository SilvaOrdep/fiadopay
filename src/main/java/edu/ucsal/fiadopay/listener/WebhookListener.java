package edu.ucsal.fiadopay.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsal.fiadopay.criptography.EncodingService;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.domain.WebhookDelivery;
import edu.ucsal.fiadopay.dto.PaymentUpdatedEvent;
import edu.ucsal.fiadopay.repo.MerchantRepository;
import edu.ucsal.fiadopay.repo.WebhookDeliveryRepository;
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

}