package com.example.auction.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Reads the Bearer token on API requests and attaches the AuthUser as a
 * request attribute. Endpoints enforce auth/roles via Auth.require helpers.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwt;

    public AuthInterceptor(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                request.setAttribute(Auth.ATTRIBUTE, jwt.parse(header.substring(7)));
            } catch (Exception ignored) {
                // invalid/expired token -> treated as anonymous
            }
        }
        return true;
    }
}
