package edu.ucsal.fiadopay.service;

import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.dto.MerchantRequest;
import edu.ucsal.fiadopay.dto.MerchantResponse;
import edu.ucsal.fiadopay.repo.MerchantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;

    public MerchantService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    public MerchantResponse createMerchant(MerchantRequest merchantRequest) {
        if (merchantRepository.existsByName(merchantRequest.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Merchant name already exists");
        }

        var merchant = Merchant.builder()
                .name(merchantRequest.name())
                .webhookUrl(merchantRequest.webhookUrl())
                .clientId(UUID.randomUUID().toString())
                .clientSecret(UUID.randomUUID().toString().replace("-", ""))
                .status(Merchant.Status.ACTIVE)
                .build();

        merchantRepository.save(merchant);

        return new MerchantResponse(merchant);
    }

    protected Merchant merchantFromAuth(String auth) {
        if (auth == null || !auth.startsWith("Bearer FAKE-")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var raw = auth.substring("Bearer FAKE-".length());
        long id;
        Merchant merchant;
        try {
            id = Long.parseLong(raw);
            merchant = findMerchantById(id);
        } catch (NumberFormatException | EntityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (merchant.getStatus() != Merchant.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return merchant;
    }

    protected Merchant findMerchantById(long id) throws EntityNotFoundException {
        return merchantRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Merchant not found"));
    }

}
