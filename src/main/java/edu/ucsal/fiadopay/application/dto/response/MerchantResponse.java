package edu.ucsal.fiadopay.application.dto.response;

import edu.ucsal.fiadopay.domain.model.Merchant;

public record MerchantResponse(String name, String webhookUrl, String clientId, String clientSecret, String status) {

    public MerchantResponse(Merchant merchant) {
        this(merchant.getName(), merchant.getWebhookUrl(), merchant.getClientId(), merchant.getClientSecret(), merchant.getStatus().name());
    }

}

