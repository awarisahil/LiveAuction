package com.example.auction.service;

import com.example.auction.auth.AuthUser;
import com.example.auction.dto.BidResult;
import com.example.auction.dto.OutboundEvent;
import com.example.auction.exception.AuctionExceptions;
import com.example.auction.model.Auction;
import com.example.auction.model.Bid;
import com.example.auction.model.User;
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
 * Bid write path. Placement runs as a single Lua script in Redis so the
 * read-check-write cycle (status/deadline check, minimum-increment check,
 * state update, anti-snipe extension, history append, idempotency record) is
 * atomic under concurrent load. Trust checks (banned account, own listing)
 * run against fresh user state just before the script.
 */
@Service
public class BidService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List<Object>> placeBidScript;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper om;
    private final AuctionService auctionService;
    private final UserRepository users;
    private final long idemTtlSeconds;
    private final int recentBidsLimit;
    private final long antiSnipeMs;

    public BidService(StringRedisTemplate redis,
                      DefaultRedisScript<List<Object>> placeBidScript,
                      ApplicationEventPublisher publisher,
                      ObjectMapper om,
                      AuctionService auctionService,
                      UserRepository users,
                      @Value("${auction.idempotency-ttl-seconds:86400}") long idemTtlSeconds,
                      @Value("${auction.recent-bids-limit:50}") int recentBidsLimit,
                      @Value("${auction.anti-snipe-ms:30000}") long antiSnipeMs) {
        this.redis = redis;
        this.placeBidScript = placeBidScript;
        this.publisher = publisher;
        this.om = om;
        this.auctionService = auctionService;
        this.users = users;
        this.idemTtlSeconds = idemTtlSeconds;
        this.recentBidsLimit = recentBidsLimit;
        this.antiSnipeMs = antiSnipeMs;
    }

    public BidResult placeBid(String auctionId, long amountCents, AuthUser auth, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new AuctionExceptions.IdempotencyKeyRequiredException();
        }
        User user = users.get(auth.id())
                .orElseThrow(() -> new AuctionExceptions.ForbiddenException("Account unavailable"));
        if (user.isBanned()) {
            throw new AuctionExceptions.ForbiddenException("This account has been suspended");
        }
        Auction auction = auctionService.get(auctionId);
        if (auth.id().equals(auction.getSellerId())) {
            throw new AuctionExceptions.ForbiddenException("You cannot bid on your own listing");
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
                user.getId(),
                user.getName(),
                String.valueOf(amountCents),
                String.valueOf(now),
                String.valueOf(idemTtlSeconds),
                String.valueOf(recentBidsLimit),
                String.valueOf(antiSnipeMs));

        long code = ((Number) result.get(0)).longValue();
        String payload = String.valueOf(result.get(1));

        return switch ((int) code) {
            case 1 -> accepted(auctionId, new Bid(bidId, auctionId, user.getId(), user.getName(), amountCents, now));
            case 0 -> BidResult.replayed(payload);
            case -1 -> throw new AuctionExceptions.BidTooLowException(Long.parseLong(payload));
            case -2 -> throw new AuctionExceptions.AuctionNotActiveException(payload);
            default -> throw new IllegalStateException("Unexpected script result: " + result);
        };
    }

    private BidResult accepted(String auctionId, Bid bid) {
        try {
            redis.opsForList().leftPush(UserRepository.bidsKey(bid.bidderId()),
                    om.writeValueAsString(bid));
            redis.opsForList().trim(UserRepository.bidsKey(bid.bidderId()), 0, 99);

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
