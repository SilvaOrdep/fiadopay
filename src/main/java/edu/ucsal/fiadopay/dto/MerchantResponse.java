package edu.ucsal.fiadopay.dto;

import edu.ucsal.fiadopay.domain.Merchant;

public record MerchantResponse(String name, String webhookUrl, String clientId, String clientSecret, String status) {

    public MerchantResponse(Merchant merchant) {
        this(merchant.getName(), merchant.getWebhookUrl(), merchant.getClientId(), merchant.getClientSecret(), merchant.getStatus().toString());
    }

}

