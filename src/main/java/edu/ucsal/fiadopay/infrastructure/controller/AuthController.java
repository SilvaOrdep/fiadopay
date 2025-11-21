package edu.ucsal.fiadopay.infrastructure.controller;

import edu.ucsal.fiadopay.application.dto.request.TokenRequest;
import edu.ucsal.fiadopay.application.dto.response.TokenResponse;
import edu.ucsal.fiadopay.application.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fiadopay/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/token")
    public TokenResponse token(@RequestBody @Valid TokenRequest req) {
        return authService.getToken(req);
    }

}
