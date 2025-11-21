package edu.ucsal.fiadopay.infrastructure.controller;

import edu.ucsal.fiadopay.application.dto.request.MerchantRequest;
import edu.ucsal.fiadopay.application.dto.response.MerchantResponse;
import edu.ucsal.fiadopay.application.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fiadopay/admin/merchants")
@RequiredArgsConstructor
public class MerchantAdminController {

    private final MerchantService merchantService;

    @PostMapping
    public MerchantResponse create(@Valid @RequestBody MerchantRequest dto) {
        return merchantService.createMerchant(dto);
    }

}
