package com.example.auction.service;

import com.example.auction.dto.OutboundEvent;
import com.example.auction.model.Auction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Countdown enforcement. Clients render countdowns locally from the server
 * supplied endsAt timestamp; this scheduler is the source of truth that flips
 * expired auctions to ENDED exactly once (HSETNX claim) and notifies watchers.
 */
@Service
public class AuctionCloser {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List<Object>> claimEndScript;
    private final AuctionService auctionService;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper om;

    public AuctionCloser(StringRedisTemplate redis,
                         DefaultRedisScript<List<Object>> claimEndScript,
                         AuctionService auctionService,
                         ApplicationEventPublisher publisher,
                         ObjectMapper om) {
        this.redis = redis;
        this.claimEndScript = claimEndScript;
        this.auctionService = auctionService;
        this.publisher = publisher;
        this.om = om;
    }

    @Scheduled(fixedDelay = 1000)
    public void closeExpired() {
        long now = Instant.now().toEpochMilli();
        Set<String> ids = redis.opsForSet().members(AuctionService.ACTIVE_INDEX);
        if (ids == null) return;

        for (String id : ids) {
            try {
                Auction auction = auctionService.get(id);
                if (auction.getEndsAt() > now) continue;

                List<Object> result = redis.execute(claimEndScript,
                        List.of(AuctionService.stateKey(id)), now);
                long code = ((Number) result.get(0)).longValue();
                if (code != 1) continue; // another scheduler instance claimed it

                redis.opsForSet().remove(AuctionService.ACTIVE_INDEX, id);
                redis.opsForSet().add("auction:index:ended", id);

                Auction ended = auctionService.get(id);
                String event = om.createObjectNode()
                        .put("type", "AUCTION_ENDED")
                        .set("auction", om.valueToTree(ended))
                        .toString();
                publisher.publishEvent(new OutboundEvent(
                        "/topic/auction/" + id + "/events", event));
            } catch (Exception ignored) {
                // skip this tick for this auction; it will be retried next second
            }
        }
    }
}
