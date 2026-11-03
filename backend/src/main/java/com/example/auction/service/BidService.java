package com.example.auction.service;

import com.example.auction.dto.BidResult;
import com.example.auction.dto.OutboundEvent;
import com.example.auction.exception.AuctionExceptions;
import com.example.auction.model.Auction;
import com.example.auction.model.Bid;
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
 * Bid write path. Placement runs as a single Lua script in Redis so the
 * read-check-write cycle (status check, minimum-increment check, state update,
 * history append, idempotency record) is atomic under concurrent load.
 */
@Service
public class BidService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List<Object>> placeBidScript;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper om;
    private final AuctionService auctionService;
    private final long idemTtlSeconds;
    private final int recentBidsLimit;

    public BidService(StringRedisTemplate redis,
                      DefaultRedisScript<List<Object>> placeBidScript,
                      ApplicationEventPublisher publisher,
                      ObjectMapper om,
                      AuctionService auctionService,
                      @Value("${auction.idempotency-ttl-seconds:86400}") long idemTtlSeconds,
                      @Value("${auction.recent-bids-limit:50}") int recentBidsLimit) {
        this.redis = redis;
        this.placeBidScript = placeBidScript;
        this.publisher = publisher;
        this.om = om;
        this.auctionService = auctionService;
        this.idemTtlSeconds = idemTtlSeconds;
        this.recentBidsLimit = recentBidsLimit;
    }

    public BidResult placeBid(String auctionId, String bidder, long amountCents, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new AuctionExceptions.IdempotencyKeyRequiredException();
        }

        String bidId = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();

        List<Object> result = redis.execute(
                placeBidScript,
                List.of(
                        AuctionService.stateKey(auctionId),
                        "auction:idem:" + idempotencyKey,
                        AuctionService.bidsKey(auctionId)),
                bidId,
                bidder,
                String.valueOf(amountCents),
                String.valueOf(now),
                String.valueOf(idemTtlSeconds),
                String.valueOf(recentBidsLimit));

        long code = ((Number) result.get(0)).longValue();
        String payload = String.valueOf(result.get(1));

        return switch ((int) code) {
            case 1 -> accepted(auctionId, new Bid(bidId, auctionId, bidder, amountCents, now));
            case 0 -> BidResult.replayed(payload);
            case -1 -> throw new AuctionExceptions.BidTooLowException(Long.parseLong(payload));
            case -2 -> throw new AuctionExceptions.AuctionNotActiveException(payload);
            default -> throw new IllegalStateException("Unexpected script result: " + result);
        };
    }

    private BidResult accepted(String auctionId, Bid bid) {
        try {
            publisher.publishEvent(new OutboundEvent(
                    "/topic/auction/" + auctionId + "/bids", om.writeValueAsString(bid)));

            Auction updated = auctionService.get(auctionId);
            String event = om.createObjectNode()
                    .put("type", "BID_ACCEPTED")
                    .set("auction", om.valueToTree(updated))
                    .toString();
            publisher.publishEvent(new OutboundEvent(
                    "/topic/auction/" + auctionId + "/events", event));
        } catch (Exception e) {
            // Bid is durably recorded; a failed broadcast must not fail the request.
        }
        return BidResult.accepted(bid.id());
    }
}
