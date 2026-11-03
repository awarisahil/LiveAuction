package com.example.auction.controller;

import com.example.auction.auth.Auth;
import com.example.auction.dto.BidResult;
import com.example.auction.dto.PlaceBidRequest;
import com.example.auction.model.Auction;
import com.example.auction.service.AuctionService;
import com.example.auction.service.BidService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auctions/{id}/bids")
public class BidController {

    private final BidService bidService;
    private final AuctionService auctionService;

    public BidController(BidService bidService, AuctionService auctionService) {
        this.bidService = bidService;
        this.auctionService = auctionService;
    }

    @PostMapping
    public BidResponse place(HttpServletRequest request,
                             @PathVariable String id,
                             @Valid @RequestBody PlaceBidRequest body,
                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        var auth = Auth.require(request);
        BidResult result = bidService.placeBid(id, body.amountCents(), auth, idempotencyKey);
        Auction auction = auctionService.get(id);
        return new BidResponse(result, auction, Instant.now().toEpochMilli());
    }

    public record BidResponse(BidResult result, Auction auction, long serverNow) {}
}
