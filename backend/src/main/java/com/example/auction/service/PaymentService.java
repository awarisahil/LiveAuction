package com.example.auction.service;

import com.example.auction.auth.AuthUser;
import com.example.auction.dto.OutboundEvent;
import com.example.auction.dto.PaymentReceipt;
import com.example.auction.exception.AuctionExceptions;
import com.example.auction.model.Auction;
import com.example.auction.repo.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wallet settlement backed by a simulated payment processor. The whole flow is
 * one atomic Lua script: winner guard, double-payment guard, balance check,
 * debit/credit of wallets, platform fee accounting, receipt, idempotency.
 */
@Service
public class PaymentService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List<Object>> payScript;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper om;
    private final AuctionService auctionService;
    private final long idemTtlSeconds;
    private final long feeBps;

    public PaymentService(StringRedisTemplate redis,
                          DefaultRedisScript<List<Object>> payScript,
                          ApplicationEventPublisher publisher,
                          ObjectMapper om,
                          AuctionService auctionService,
                          @Value("${auction.idempotency-ttl-seconds:86400}") long idemTtlSeconds,
                          @Value("${auction.platform-fee-bps:500}") long feeBps) {
        this.redis = redis;
        this.payScript = payScript;
        this.publisher = publisher;
        this.om = om;
        this.auctionService = auctionService;
        this.idemTtlSeconds = idemTtlSeconds;
        this.feeBps = feeBps;
    }

    public PaymentReceipt pay(String auctionId, AuthUser auth, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new AuctionExceptions.IdempotencyKeyRequiredException();
        }
        Auction auction = auctionService.get(auctionId);
        if (!auth.id().equals(auction.getLeader())) {
            throw new AuctionExceptions.PaymentRejectedException("Only the winning bidder can pay");
        }

        String receiptId = UUID.randomUUID().toString();
        List<Object> result = redis.execute(
                payScript,
                List.of(
                        AuctionService.stateKey(auctionId),
                        "auction:idem:" + idempotencyKey,
                        AuctionService.paymentKey(auctionId),
                        UserRepository.walletKey(auth.id()),
                        UserRepository.walletKey(auction.getSellerId())),
                receiptId,
                auth.id(),
                String.valueOf(Instant.now().toEpochMilli()),
                String.valueOf(idemTtlSeconds),
                String.valueOf(feeBps));

        long code = ((Number) result.get(0)).longValue();
        String payload = String.valueOf(result.get(1));

        return switch ((int) code) {
            case 1 -> settle(auctionId, auth.id(), receipt(auctionId));
            case 0 -> settleQuiet(auctionId, receipt(auctionId)); // idempotent replay
            case -2 -> throw new AuctionExceptions.AuctionNotActiveException(payload);
            case -3 -> throw new AuctionExceptions.PaymentRejectedException(
                    "Only the winning bidder can pay for this auction");
            case -5 -> throw new AuctionExceptions.PaymentRejectedException("Auction already settled");
            case -6 -> throw new AuctionExceptions.PaymentRejectedException(
                    "Insufficient wallet balance — deposit funds first (short " + payload + " cents)");
            default -> throw new IllegalStateException("Unexpected script result: " + result);
        };
    }

    private PaymentReceipt settle(String auctionId, String payerId, PaymentReceipt receipt) {
        try {
            redis.opsForList().leftPush(UserRepository.paymentsKey(payerId),
                    om.writeValueAsString(receipt));
            redis.opsForList().trim(UserRepository.paymentsKey(payerId), 0, 49);
            String event = om.createObjectNode()
                    .put("type", "PAYMENT_COMPLETED")
                    .set("receipt", om.valueToTree(receipt))
                    .toString();
            publisher.publishEvent(new OutboundEvent(
                    "/topic/auction/" + auctionId + "/events", event));
        } catch (Exception ignored) {
            // receipt is durable; broadcast failure must not fail the request
        }
        return receipt;
    }

    private PaymentReceipt settleQuiet(String auctionId, PaymentReceipt receipt) {
        return settle(auctionId, receipt.payer(), receipt);
    }

    private PaymentReceipt receipt(String auctionId) {
        try {
            String json = auctionService.paymentJson(auctionId);
            if (json != null) {
                var tree = om.readTree(json);
                return new PaymentReceipt(
                        tree.get("id").asText(),
                        auctionId,
                        tree.get("payer").asText(),
                        tree.get("amountCents").asLong(),
                        tree.has("feeCents") ? tree.get("feeCents").asLong() : 0,
                        tree.get("paidAt").asLong());
            }
        } catch (Exception ignored) {
            // fall through to state-derived receipt
        }
        Auction auction = auctionService.get(auctionId);
        return new PaymentReceipt("unknown", auctionId, auction.getLeader(),
                auction.getCurrentBidCents(), 0, Instant.now().toEpochMilli());
    }
}
