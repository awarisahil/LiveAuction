package com.example.auction.controller;

import com.example.auction.exception.AuctionExceptions;
import com.example.auction.model.User;
import com.example.auction.repo.UserRepository;
import com.example.auction.service.AuctionService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final AuctionService auctionService;
    private final StringRedisTemplate redis;

    public UserController(UserRepository users, AuctionService auctionService,
                          StringRedisTemplate redis) {
        this.users = users;
        this.auctionService = auctionService;
        this.redis = redis;
    }

    /** Public profile with platform activity stats. */
    @GetMapping("/{id}")
    public Map<String, Object> profile(@PathVariable String id) {
        User user = users.get(id)
                .orElseThrow(() -> new AuctionExceptions.ValidationException("User not found"));
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("name", user.getName());
        profile.put("avatarEmoji", user.getAvatarEmoji());
        profile.put("bio", user.getBio());
        profile.put("role", user.getRole());
        profile.put("createdAt", user.getCreatedAt());
        Long bidCount = redis.opsForList().size(UserRepository.bidsKey(id));
        profile.put("bidsPlaced", bidCount == null ? 0 : bidCount);
        profile.put("auctionsWon", auctionService.countWonBy(id));
        profile.put("listings", auctionService.countListingsBy(id));
        return profile;
    }
}
