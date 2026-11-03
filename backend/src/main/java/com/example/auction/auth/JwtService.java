package com.example.auction.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlMillis;

    public JwtService(@Value("${auction.jwt-secret}") String secret,
                      @Value("${auction.jwt-ttl-hours:24}") long ttlHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlHours * 3_600_000L;
    }

    public String issue(AuthUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.id())
                .claim("name", user.name())
                .claim("email", user.email())
                .claim("role", user.role())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    public AuthUser parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthUser(
                c.getSubject(),
                c.get("name", String.class),
                c.get("email", String.class),
                c.get("role", String.class));
    }
}
