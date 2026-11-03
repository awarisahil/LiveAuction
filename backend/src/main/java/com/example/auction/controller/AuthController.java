package com.example.auction.controller;

import com.example.auction.auth.AuthUser;
import com.example.auction.dto.LoginRequest;
import com.example.auction.dto.RegisterRequest;
import com.example.auction.dto.UserView;
import com.example.auction.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        UserView user = authService.register(request);
        return response(authService.login(new LoginRequest(request.email(), request.password())));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        return response(authService.login(request));
    }

    private Map<String, Object> response(AuthUser user) {
        return Map.of("token", authService.issueToken(user), "user", user);
    }
}
