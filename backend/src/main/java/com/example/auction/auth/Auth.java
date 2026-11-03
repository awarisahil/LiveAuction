package com.example.auction.auth;

import com.example.auction.exception.AuctionExceptions;
import jakarta.servlet.http.HttpServletRequest;

/** Helpers to read and enforce the authenticated principal on a request. */
public final class Auth {

    public static final String ATTRIBUTE = "authUser";

    private Auth() {}

    public static AuthUser current(HttpServletRequest request) {
        Object user = request.getAttribute(ATTRIBUTE);
        return user instanceof AuthUser auth ? auth : null;
    }

    public static AuthUser require(HttpServletRequest request) {
        AuthUser user = current(request);
        if (user == null) {
            throw new AuctionExceptions.UnauthorizedException();
        }
        return user;
    }

    public static AuthUser requireRole(HttpServletRequest request, String role) {
        AuthUser user = require(request);
        if (!role.equals(user.role()) && !user.isAdmin()) {
            throw new AuctionExceptions.ForbiddenException("Requires role " + role);
        }
        return user;
    }
}
