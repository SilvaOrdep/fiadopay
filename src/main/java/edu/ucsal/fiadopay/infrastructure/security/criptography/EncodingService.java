package edu.ucsal.fiadopay.infrastructure.security.criptography;

import lombok.Setter;
import org.springframework.stereotype.Service;

@Setter
@Service
public class EncodingService {
    private Encodable encodingStrategy;

    public EncodingService(Encodable encodingStrategy){
        this.encodingStrategy = encodingStrategy;
    }

    public String executeEncodingStrategy(String payload, String secret){
        return encodingStrategy.encode(payload, secret);
    }
}
