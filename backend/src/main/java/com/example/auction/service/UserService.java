package com.example.auction.service;

import com.example.auction.auth.AuthUser;
import com.example.auction.dto.UpdateProfileRequest;
import com.example.auction.dto.UserView;
import com.example.auction.exception.AuctionExceptions;
import com.example.auction.model.User;
import com.example.auction.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository users;
    private final StringRedisTemplate redis;
    private final long maxWalletCents;

    public UserService(UserRepository users,
                       StringRedisTemplate redis,
                       @Value("${auction.max-wallet-cents:100000000}") long maxWalletCents) {
        this.users = users;
        this.redis = redis;
        this.maxWalletCents = maxWalletCents;
    }

    public User requireUser(String id) {
        return users.get(id)
                .filter(u -> !u.isBanned())
                .orElseThrow(() -> new AuctionExceptions.ForbiddenException("Account unavailable"));
    }

    public UserView profile(AuthUser auth) {
        return users.get(auth.id()).map(UserView::of)
                .orElseThrow(AuctionExceptions.UnauthorizedException::new);
    }

    public UserView updateProfile(AuthUser auth, UpdateProfileRequest request) {
        User user = requireUser(auth.id());
        if (request.name() != null && !request.name().isBlank()) user.setName(request.name().trim());
        if (request.avatarEmoji() != null) user.setAvatarEmoji(request.avatarEmoji());
        if (request.bio() != null) user.setBio(request.bio());
        users.save(user);
        return UserView.of(user);
    }

    /** Simulated card processor: instantly credits the wallet. */
    public UserView deposit(AuthUser auth, long amountCents) {
        User user = requireUser(auth.id());
        if (amountCents < 100) {
            throw new AuctionExceptions.ValidationException("Minimum deposit is $1.00");
        }
        long newBalance = redis.opsForHash().increment(
                UserRepository.key(auth.id()), "balanceCents", amountCents);
        if (newBalance > maxWalletCents) {
            redis.opsForHash().increment(UserRepository.key(auth.id()), "balanceCents", -amountCents);
            throw new AuctionExceptions.ValidationException("Wallet balance cap reached");
        }
        user.setBalanceCents(newBalance);
        return UserView.of(user);
    }

    public List<UserView> listAllForAdmin() {
        return users.listAll().stream().map(UserView::of).toList();
    }

    public UserView adminUpdate(String userId, String role, Boolean banned, AuthUser admin) {
        User target = users.get(userId)
                .orElseThrow(() -> new AuctionExceptions.ValidationException("User not found"));
        if (target.getId().equals(admin.id())) {
            throw new AuctionExceptions.ValidationException("You cannot modify your own account");
        }
        if (target.isAdmin() && (banned != null || (role != null && !"ADMIN".equals(role)))) {
            throw new AuctionExceptions.ForbiddenException("Other admins cannot be modified");
        }
        if (role != null) {
            String r = role.toUpperCase();
            if (!Set.of("BUYER", "SELLER", "ADMIN").contains(r)) {
                throw new AuctionExceptions.ValidationException("Invalid role");
            }
            target.setRole(r);
        }
        if (banned != null) target.setBanned(banned);
        users.save(target);
        return UserView.of(target);
    }
}
