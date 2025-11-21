package edu.ucsal.fiadopay.infrastructure.security.criptography;

import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class HmacEncodingStrategy implements Encodable{
    @Override
    public String encode(String payload, String secret) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes()));
        } catch (Exception e){ return ""; }
    }
}
