package com.example.auction.auth;

/** Authenticated principal extracted from the JWT and stored as a request attribute. */
public record AuthUser(String id, String name, String email, String role) {
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
