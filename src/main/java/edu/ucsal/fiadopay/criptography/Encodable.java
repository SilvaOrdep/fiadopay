package edu.ucsal.fiadopay.criptography;

public interface Encodable {
    String encode(String payload, String secret);
}
