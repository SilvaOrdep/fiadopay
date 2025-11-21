package edu.ucsal.fiadopay.infrastructure.security.criptography;

public interface Encodable {
    String encode(String payload, String secret);
}
