package com.example.auction.service;

import com.example.auction.dto.OutboundEvent;
import com.example.auction.dto.PaymentReceipt;
import com.example.auction.exception.AuctionExceptions;
import com.example.auction.model.Auction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Simulated payment gateway. Settlement is atomic in a Lua script: only the
 * recorded winner can pay, only after the auction ended, only once, and always
 * for the exact winning amount taken from server-side state — never from a
 * client-supplied value. Idempotency keys make safe retries replay the receipt.
 */
@Service
public class PaymentService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List<Object>> payScript;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper om;
    private final AuctionService auctionService;
    private final long idemTtlSeconds;

    public PaymentService(StringRedisTemplate redis,
                          DefaultRedisScript<List<Object>> payScript,
                          ApplicationEventPublisher publisher,
                          ObjectMapper om,
                          AuctionService auctionService,
                          @Value("${auction.idempotency-ttl-seconds:86400}") long idemTtlSeconds) {
        this.redis = redis;
        this.payScript = payScript;
        this.publisher = publisher;
        this.om = om;
        this.auctionService = auctionService;
        this.idemTtlSeconds = idemTtlSeconds;
    }

    public PaymentReceipt pay(String auctionId, String payer, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new AuctionExceptions.IdempotencyKeyRequiredException();
        }

        String receiptId = UUID.randomUUID().toString();
        List<Object> result = redis.execute(
                payScript,
                List.of(
                        AuctionService.stateKey(auctionId),
                        "auction:idem:" + idempotencyKey,
                        AuctionService.paymentKey(auctionId)),
                receiptId,
                payer,
                Instant.now().toEpochMilli(),
                idemTtlSeconds);

        long code = ((Number) result.get(0)).longValue();
        String payload = String.valueOf(result.get(1));

        return switch ((int) code) {
            case 1 -> settle(auctionId, new PaymentReceipt(
                    receiptId, auctionId, payer,
                    auctionService.get(auctionId).getCurrentBidCents(),
                    Instant.now().toEpochMilli()));
            case 0 -> receipt(auctionId, payload); // idempotent replay
            case -2 -> throw new AuctionExceptions.AuctionNotActiveException(payload);
            case -3 -> throw new AuctionExceptions.PaymentRejectedException(
                    "Only the winning bidder (" + payload + ") can pay for this auction");
            case -5 -> throw new AuctionExceptions.PaymentRejectedException(
                    "Auction already settled");
            default -> throw new IllegalStateException("Unexpected script result: " + result);
        };
    }

    private PaymentReceipt settle(String auctionId, PaymentReceipt receipt) {
        try {
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

    private PaymentReceipt receipt(String auctionId, String receiptId) {
        try {
            String json = auctionService.paymentJson(auctionId);
            if (json != null) {
                var tree = om.readTree(json);
                return new PaymentReceipt(
                        tree.get("id").asText(),
                        auctionId,
                        tree.get("payer").asText(),
                        tree.get("amountCents").asLong(),
                        tree.get("paidAt").asLong());
            }
        } catch (Exception ignored) {
            // fall through to state-derived receipt
        }
        Auction auction = auctionService.get(auctionId);
        return new PaymentReceipt(receiptId, auctionId, auction.getLeader(),
                auction.getCurrentBidCents(), Instant.now().toEpochMilli());
    }
}
