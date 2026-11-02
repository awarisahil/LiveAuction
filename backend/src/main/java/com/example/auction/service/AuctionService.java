package com.example.auction.service;

import com.example.auction.model.Auction;
import com.example.auction.model.Bid;
import com.example.auction.exception.AuctionExceptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read side of the auction catalog. The catalog JSON lives in a Redis string
 * key while the hot bidding fields are mirrored in a hash that the Lua scripts
 * mutate atomically; reads merge the two so callers always see fresh state.
 */
@Service
public class AuctionService {

    public static final String ACTIVE_INDEX = "auction:index:active";

    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public AuctionService(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis;
        this.om = om;
    }

    public static String key(String id) { return "auction:" + id; }
    public static String stateKey(String id) { return "auction:" + id + ":state"; }
    public static String bidsKey(String id) { return "auction:" + id + ":bids"; }
    public static String paymentKey(String id) { return "auction:" + id + ":payment"; }

    public Auction get(String id) {
        String json = redis.opsForValue().get(key(id));
        if (json == null) {
            throw new AuctionExceptions.AuctionNotFoundException(id);
        }
        try {
            Auction auction = om.readValue(json, Auction.class);
            mergeState(auction, redis.opsForHash().entries(stateKey(id)));
            return auction;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read auction " + id, e);
        }
    }

    public List<Auction> listActive() {
        Set<String> ids = redis.opsForSet().members(ACTIVE_INDEX);
        List<Auction> auctions = new ArrayList<>();
        if (ids == null) return auctions;
        for (String id : ids) {
            try {
                auctions.add(get(id));
            } catch (AuctionExceptions.AuctionNotFoundException ignored) {
                // index entry without catalog data; skip
            }
        }
        auctions.sort((a, b) -> Long.compare(a.getEndsAt(), b.getEndsAt()));
        return auctions;
    }

    public List<Bid> recentBids(String id, int limit) {
        List<String> raw = redis.opsForList().range(bidsKey(id), 0, limit - 1);
        List<Bid> bids = new ArrayList<>();
        if (raw == null) return bids;
        for (String json : raw) {
            try {
                bids.add(om.readValue(json, Bid.class));
            } catch (Exception ignored) {
                // skip malformed entry
            }
        }
        return bids;
    }

    public String paymentJson(String id) {
        return redis.opsForValue().get(paymentKey(id));
    }

    private void mergeState(Auction auction, Map<Object, Object> state) {
        if (state == null || state.isEmpty()) return;
        Object current = state.get("currentBidCents");
        if (current != null) auction.setCurrentBidCents(Long.parseLong(current.toString()));
        Object leader = state.get("leader");
        if (leader != null) auction.setLeader(leader.toString());
        Object status = state.get("status");
        if (status != null) auction.setStatus(status.toString());
        Object bidCount = state.get("bidCount");
        if (bidCount != null) auction.setBidCount(Long.parseLong(bidCount.toString()));
        Object paid = state.get("paid");
        if (paid != null) auction.setPaid("1".equals(paid.toString()));
    }
}
