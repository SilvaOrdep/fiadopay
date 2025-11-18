package edu.ucsal.fiadopay.criptography;

public class EncodingStrategy {
    private Encodable encodingStrategy;

    public EncodingStrategy(Encodable encodingStrategy){
        this.encodingStrategy = encodingStrategy;
    }

    public void setEncodingStrategy(Encodable encodingStrategy) {
        this.encodingStrategy = encodingStrategy;
    }

    public String executeEncodingStrategy(String payload, String secret){
        return encodingStrategy.encode(payload, secret);
    }
}
