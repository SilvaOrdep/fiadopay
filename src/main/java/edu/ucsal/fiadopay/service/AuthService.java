package edu.ucsal.fiadopay.service;

import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.dto.TokenRequest;
import edu.ucsal.fiadopay.dto.TokenResponse;
import edu.ucsal.fiadopay.repo.MerchantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final MerchantRepository merchantRepository;

    public AuthService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    public TokenResponse getToken(TokenRequest tokenRequest) {
        var merchant = merchantRepository.findByClientId(tokenRequest.client_id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        if (!merchant.getClientSecret().equals(tokenRequest.client_secret())
                || merchant.getStatus() != Merchant.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return new TokenResponse("FAKE-" + merchant.getId(), "Bearer", 3600);
    }

}
