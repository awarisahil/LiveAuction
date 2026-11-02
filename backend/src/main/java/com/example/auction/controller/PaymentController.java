package com.example.auction.controller;

import com.example.auction.dto.PayRequest;
import com.example.auction.dto.PaymentReceipt;
import com.example.auction.model.Auction;
import com.example.auction.service.AuctionService;
import com.example.auction.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auctions/{id}/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final AuctionService auctionService;

    public PaymentController(PaymentService paymentService, AuctionService auctionService) {
        this.paymentService = paymentService;
        this.auctionService = auctionService;
    }

    @PostMapping
    public PaymentResponse pay(@PathVariable String id,
                               @Valid @RequestBody PayRequest request,
                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        PaymentReceipt receipt = paymentService.pay(id, request.payer(), idempotencyKey);
        Auction auction = auctionService.get(id);
        return new PaymentResponse(receipt, auction, Instant.now().toEpochMilli());
    }

    public record PaymentResponse(PaymentReceipt receipt, Auction auction, long serverNow) {}
}
