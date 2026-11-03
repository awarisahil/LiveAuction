package com.example.auction.controller;

import com.example.auction.auth.Auth;
import com.example.auction.auth.AuthUser;
import com.example.auction.dto.DepositRequest;
import com.example.auction.dto.PaymentReceipt;
import com.example.auction.dto.UpdateProfileRequest;
import com.example.auction.dto.UserView;
import com.example.auction.model.Auction;
import com.example.auction.model.Bid;
import com.example.auction.repo.UserRepository;
import com.example.auction.service.AuctionService;
import com.example.auction.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService userService;
    private final AuctionService auctionService;
    private final UserRepository users;
    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public MeController(UserService userService, AuctionService auctionService,
                        UserRepository users, StringRedisTemplate redis, ObjectMapper om) {
        this.userService = userService;
        this.auctionService = auctionService;
        this.users = users;
        this.redis = redis;
        this.om = om;
    }

    @GetMapping
    public UserView me(HttpServletRequest request) {
        return userService.profile(Auth.require(request));
    }

    @PatchMapping
    public UserView update(HttpServletRequest request,
                           @Valid @RequestBody UpdateProfileRequest body) {
        return userService.updateProfile(Auth.require(request), body);
    }

    @PostMapping("/wallet/deposit")
    public UserView deposit(HttpServletRequest request,
                            @Valid @RequestBody DepositRequest body) {
        return userService.deposit(Auth.require(request), body.amountCents());
    }

    @GetMapping("/bids")
    public List<BidView> myBids(HttpServletRequest request) {
        AuthUser auth = Auth.require(request);
        List<String> raw = redis.opsForList().range(UserRepository.bidsKey(auth.id()), 0, 49);
        List<BidView> views = new ArrayList<>();
        if (raw == null) return views;
        for (String json : raw) {
            try {
                Bid bid = om.readValue(json, Bid.class);
                String title = auctionService.get(bid.auctionId()).getTitle();
                views.add(new BidView(bid.id(), bid.auctionId(), title, bid.amountCents(), bid.at()));
            } catch (Exception ignored) {
                // auction gone; skip
            }
        }
        return views;
    }

    @GetMapping("/winnings")
    public List<Auction> winnings(HttpServletRequest request) {
        AuthUser auth = Auth.require(request);
        return auctionService.listEnded().stream()
                .filter(a -> auth.id().equals(a.getLeader()))
                .toList();
    }

    @GetMapping("/listings")
    public List<Auction> listings(HttpServletRequest request) {
        AuthUser auth = Auth.require(request);
        List<Auction> all = new ArrayList<>(auctionService.listActive(null, null));
        all.addAll(auctionService.listEnded());
        return all.stream().filter(a -> auth.id().equals(a.getSellerId())).toList();
    }

    @GetMapping("/payments")
    public List<PaymentReceipt> payments(HttpServletRequest request) {
        AuthUser auth = Auth.require(request);
        List<String> raw = redis.opsForList().range(UserRepository.paymentsKey(auth.id()), 0, 49);
        List<PaymentReceipt> receipts = new ArrayList<>();
        if (raw == null) return receipts;
        for (String json : raw) {
            try {
                var tree = om.readTree(json);
                receipts.add(new PaymentReceipt(
                        tree.get("id").asText(), "",
                        tree.get("payer").asText(),
                        tree.get("amountCents").asLong(),
                        tree.has("feeCents") ? tree.get("feeCents").asLong() : 0,
                        tree.get("paidAt").asLong()));
            } catch (Exception ignored) {
                // skip malformed
            }
        }
        return receipts;
    }

    @PutMapping("/watchlist/{auctionId}")
    public void watch(HttpServletRequest request, @PathVariable String auctionId) {
        AuthUser auth = Auth.require(request);
        auctionService.get(auctionId);
        redis.opsForSet().add(UserRepository.watchKey(auth.id()), auctionId);
    }

    @DeleteMapping("/watchlist/{auctionId}")
    public void unwatch(HttpServletRequest request, @PathVariable String auctionId) {
        AuthUser auth = Auth.require(request);
        redis.opsForSet().remove(UserRepository.watchKey(auth.id()), auctionId);
    }

    @GetMapping("/watchlist")
    public List<Auction> watchlist(HttpServletRequest request) {
        AuthUser auth = Auth.require(request);
        Set<String> ids = redis.opsForSet().members(UserRepository.watchKey(auth.id()));
        List<Auction> auctions = new ArrayList<>();
        if (ids == null) return auctions;
        for (String id : ids) {
            try {
                auctions.add(auctionService.get(id));
            } catch (Exception ignored) {
                // dropped auction; skip
            }
        }
        return auctions;
    }

    public record BidView(String id, String auctionId, String auctionTitle,
                          long amountCents, long at) {}
}
