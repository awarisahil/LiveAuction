package com.example.auction.controller;

import com.example.auction.auth.Auth;
import com.example.auction.auth.AuthUser;
import com.example.auction.dto.AdminUserUpdateRequest;
import com.example.auction.dto.UserView;
import com.example.auction.model.Auction;
import com.example.auction.repo.UserRepository;
import com.example.auction.service.AuctionService;
import com.example.auction.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;
    private final AuctionService auctionService;
    private final UserRepository users;
    private final StringRedisTemplate redis;

    public AdminController(UserService userService, AuctionService auctionService,
                           UserRepository users, StringRedisTemplate redis) {
        this.userService = userService;
        this.auctionService = auctionService;
        this.users = users;
        this.redis = redis;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(HttpServletRequest request) {
        Auth.requireRole(request, "ADMIN");
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("users", users.count());
        stats.put("activeAuctions", size(AuctionService.ACTIVE_INDEX));
        stats.put("endedAuctions", size(AuctionService.ENDED_INDEX));
        stats.put("totalBids", number("platform:bids"));
        stats.put("grossMerchandiseValueCents", number("platform:gmv"));
        stats.put("platformRevenueCents", number("platform:revenue"));
        return stats;
    }

    @GetMapping("/users")
    public List<UserView> users(HttpServletRequest request) {
        Auth.requireRole(request, "ADMIN");
        return userService.listAllForAdmin();
    }

    @PatchMapping("/users/{id}")
    public UserView updateUser(HttpServletRequest request, @PathVariable String id,
                               @Valid @RequestBody AdminUserUpdateRequest body) {
        AuthUser admin = Auth.requireRole(request, "ADMIN");
        return userService.adminUpdate(id, body.role(), body.banned(), admin);
    }

    @GetMapping("/auctions")
    public List<Auction> auctions(HttpServletRequest request) {
        Auth.requireRole(request, "ADMIN");
        List<Auction> all = new ArrayList<>(auctionService.listActive(null, null));
        all.addAll(auctionService.listEnded());
        return all;
    }

    @PostMapping("/auctions/{id}/cancel")
    public Auction cancel(HttpServletRequest request, @PathVariable String id) {
        return auctionService.cancel(id, Auth.requireRole(request, "ADMIN"));
    }

    private long size(String key) {
        Long n = redis.opsForSet().size(key);
        return n == null ? 0 : n;
    }

    private long number(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0 : Long.parseLong(value);
    }
}
