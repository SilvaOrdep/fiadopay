package edu.ucsal.fiadopay.infrastructure.controller;

import edu.ucsal.fiadopay.infrastructure.annotation.RateLimit;
import edu.ucsal.fiadopay.application.dto.request.PaymentRequest;
import edu.ucsal.fiadopay.application.dto.response.PaymentResponse;
import edu.ucsal.fiadopay.application.dto.request.RefundRequest;
import edu.ucsal.fiadopay.application.service.PaymentService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.Map;

@RestController
@RequestMapping("/fiadopay/gateway")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    @PostMapping("/payments")
    @SecurityRequirement(name = "bearerAuth")
    @RateLimit
    public ResponseEntity<PaymentResponse> create(@Parameter(hidden = true) @RequestHeader("Authorization") String auth, @RequestHeader(value = "Idempotency-Key", required = false) String idemKey, @RequestBody @Valid PaymentRequest req) {
        var resp = service.createPayment(auth, idemKey, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping("/payments/{id}")
    public PaymentResponse get(@PathVariable String id) {
        return service.getPayment(id);
    }

    @PostMapping("/refunds")
    @RateLimit(maxRequest = 5)
    @SecurityRequirement(name = "bearerAuth")
    public Map<String, Object> refund(@Parameter(hidden = true) @RequestHeader("Authorization") String auth, @RequestBody @Valid RefundRequest body) {
        return service.refund(auth, body.paymentId());
    }

}
