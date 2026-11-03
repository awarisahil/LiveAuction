package com.example.auction.controller;

import com.example.auction.auth.Auth;
import com.example.auction.dto.CreateAuctionRequest;
import com.example.auction.model.Auction;
import com.example.auction.model.Bid;
import com.example.auction.service.AuctionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/auctions")
public class AuctionController {

    private final AuctionService auctionService;

    public AuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @GetMapping
    public AuctionListResponse list(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String category) {
        return new AuctionListResponse(auctionService.listActive(q, category), now());
    }

    @GetMapping("/{id}")
    public AuctionDetailResponse detail(@PathVariable String id) {
        Auction auction = auctionService.get(id);
        List<Bid> recent = auctionService.recentBids(id, 50);
        return new AuctionDetailResponse(auction, now(), recent);
    }

    @PostMapping
    public Auction create(HttpServletRequest request,
                          @Valid @RequestBody CreateAuctionRequest body) {
        return auctionService.create(Auth.requireRole(request, "SELLER"), body);
    }

    private static long now() {
        return Instant.now().toEpochMilli();
    }

    public record AuctionListResponse(List<Auction> auctions, long serverNow) {}
    public record AuctionDetailResponse(Auction auction, long serverNow, List<Bid> recentBids) {}
}
