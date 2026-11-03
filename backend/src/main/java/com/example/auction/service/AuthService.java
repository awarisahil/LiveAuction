package com.example.auction.service;

import com.example.auction.auth.AuthUser;
import com.example.auction.auth.JwtService;
import com.example.auction.dto.LoginRequest;
import com.example.auction.dto.RegisterRequest;
import com.example.auction.dto.UserView;
import com.example.auction.exception.AuctionExceptions;
import com.example.auction.model.User;
import com.example.auction.repo.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private static final Set<String> REGISTERABLE_ROLES = Set.of("BUYER", "SELLER");

    private final UserRepository users;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    public UserView register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        String role = request.role().trim().toUpperCase();
        if (!REGISTERABLE_ROLES.contains(role)) {
            throw new AuctionExceptions.ValidationException("Role must be BUYER or SELLER");
        }
        if (users.byEmail(email).isPresent()) {
            throw new AuctionExceptions.ConflictException("An account with this email already exists");
        }
        User user = new User();
        user.setId(UUID.randomUUID().toString().substring(0, 12));
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRole(role);
        user.setAvatarEmoji("\uD83D\uDC64");
        user.setBio("");
        user.setBalanceCents(0);
        user.setCreatedAt(Instant.now().toEpochMilli());
        users.save(user);
        return UserView.of(user);
    }

    public AuthUser login(LoginRequest request) {
        User user = users.byEmail(request.email())
                .orElseThrow(() -> new AuctionExceptions.UnauthorizedException("Invalid credentials"));
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuctionExceptions.UnauthorizedException("Invalid credentials");
        }
        if (user.isBanned()) {
            throw new AuctionExceptions.ForbiddenException("This account has been suspended");
        }
        return new AuthUser(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    public String issueToken(AuthUser user) {
        return jwt.issue(user);
    }
}
