package com.example.auction.service;

import com.example.auction.auth.AuthUser;
import com.example.auction.dto.CreateAuctionRequest;
import com.example.auction.dto.OutboundEvent;
import com.example.auction.exception.AuctionExceptions;
import com.example.auction.model.Auction;
import com.example.auction.model.Bid;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

/**
 * Auction catalog. Catalog JSON lives in a string key; hot bidding fields are
 * mirrored in a hash that Lua scripts mutate atomically; reads merge the two.
 */
@Service
public class AuctionService {

    public static final String ACTIVE_INDEX = "auction:index:active";
    public static final String ENDED_INDEX = "auction:index:ended";
    public static final Set<String> CATEGORIES = Set.of(
            "ELECTRONICS", "FASHION", "COLLECTIBLES", "HOME", "ART", "SPORTS", "VEHICLES", "OTHER");

    private final StringRedisTemplate redis;
    private final ObjectMapper om;
    private final ApplicationEventPublisher publisher;
    private final DefaultRedisScript<List<Object>> setStatusScript;

    public AuctionService(StringRedisTemplate redis,
                          ObjectMapper om,
                          ApplicationEventPublisher publisher,
                          DefaultRedisScript<List<Object>> setStatusScript) {
        this.redis = redis;
        this.om = om;
        this.publisher = publisher;
        this.setStatusScript = setStatusScript;
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

    public List<Auction> listActive(String query, String category) {
        Set<String> ids = redis.opsForSet().members(ACTIVE_INDEX);
        List<Auction> auctions = new ArrayList<>();
        if (ids == null) return auctions;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String cat = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        for (String id : ids) {
            try {
                Auction a = get(id);
                if (!q.isEmpty() && !a.getTitle().toLowerCase(Locale.ROOT).contains(q)
                        && !(a.getDescription() != null && a.getDescription().toLowerCase(Locale.ROOT).contains(q))) {
                    continue;
                }
                if (!cat.isEmpty() && !cat.equals(a.getCategory())) {
                    continue;
                }
                auctions.add(a);
            } catch (AuctionExceptions.AuctionNotFoundException ignored) {
                // index entry without catalog data; skip
            }
        }
        auctions.sort((a, b) -> Long.compare(a.getEndsAt(), b.getEndsAt()));
        return auctions;
    }

    public List<Auction> listEnded() {
        Set<String> ids = redis.opsForSet().members(ENDED_INDEX);
        List<Auction> auctions = new ArrayList<>();
        if (ids == null) return auctions;
        for (String id : ids) {
            try {
                auctions.add(get(id));
            } catch (AuctionExceptions.AuctionNotFoundException ignored) {
                // skip
            }
        }
        auctions.sort((a, b) -> Long.compare(b.getEndsAt(), a.getEndsAt()));
        return auctions;
    }

    public Auction create(AuthUser seller, CreateAuctionRequest request) {
        String category = request.category().trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(category)) {
            throw new AuctionExceptions.ValidationException(
                    "Unknown category; use one of " + String.join(", ", CATEGORIES));
        }
        long now = Instant.now().toEpochMilli();

        Auction auction = new Auction();
        auction.setId(UUID.randomUUID().toString().substring(0, 8));
        auction.setTitle(request.title().trim());
        auction.setDescription(request.description() == null ? "" : request.description().trim());
        auction.setCategory(category);
        auction.setSellerId(seller.id());
        auction.setSellerName(seller.name());
        auction.setImageEmoji(request.imageEmoji() == null || request.imageEmoji().isBlank()
                ? "\uD83D\uDCE6" : request.imageEmoji());
        auction.setImageUrl(request.imageUrl() == null ? "" : request.imageUrl().trim());
        auction.setStartPriceCents(request.startPriceCents());
        auction.setMinIncrementCents(request.minIncrementCents());
        auction.setCurrentBidCents(request.startPriceCents());
        auction.setStatus("ACTIVE");
        auction.setStartsAt(now);
        auction.setEndsAt(now + request.durationMinutes() * 60_000L);
        persistNew(auction);
        return auction;
    }

    public void persistNew(Auction auction) {
        try {
            redis.opsForValue().set(key(auction.getId()), om.writeValueAsString(auction));
            redis.opsForHash().putAll(stateKey(auction.getId()), Map.of(
                    "currentBidCents", String.valueOf(auction.getCurrentBidCents()),
                    "minIncrementCents", String.valueOf(auction.getMinIncrementCents()),
                    "status", auction.getStatus(),
                    "bidCount", "0",
                    "endsAt", String.valueOf(auction.getEndsAt()),
                    "sellerId", auction.getSellerId()));
            redis.opsForSet().add(ACTIVE_INDEX, auction.getId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist auction " + auction.getId(), e);
        }
    }

    /** Admin may cancel anytime; the seller only while there are no bids. Refund-free because payment happens at settlement. */
    public Auction cancel(String id, AuthUser caller) {
        Auction auction = get(id);
        boolean admin = caller.isAdmin();
        boolean ownListing = caller.id().equals(auction.getSellerId());
        if (!admin && !ownListing) {
            throw new AuctionExceptions.ForbiddenException("Only the seller or an admin can cancel this auction");
        }
        if (!admin && auction.getBidCount() > 0) {
            throw new AuctionExceptions.ConflictException("Auctions with bids can only be cancelled by an admin");
        }
        List<Object> result = redis.execute(setStatusScript,
                List.of(stateKey(id)), "ACTIVE", "CANCELLED");
        long code = ((Number) result.get(0)).longValue();
        if (code != 1) {
            throw new AuctionExceptions.AuctionNotActiveException(String.valueOf(result.get(1)));
        }
        redis.opsForSet().remove(ACTIVE_INDEX, id);
        redis.opsForSet().add(ENDED_INDEX, id);

        Auction cancelled = get(id);
        try {
            String event = om.createObjectNode()
                    .put("type", "AUCTION_CANCELLED")
                    .set("auction", om.valueToTree(cancelled))
                    .toString();
            publisher.publishEvent(new OutboundEvent(
                    "/topic/auction/" + id + "/events", event));
        } catch (Exception ignored) {
            // state is durable; broadcast failure must not fail the request
        }
        return cancelled;
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

    public long countWonBy(String userId) {
        return listEnded().stream().filter(a -> userId.equals(a.getLeader())).count();
    }

    public long countListingsBy(String userId) {
        long n = listActive(null, null).stream().filter(a -> userId.equals(a.getSellerId())).count();
        return n + listEnded().stream().filter(a -> userId.equals(a.getSellerId())).count();
    }

    private void mergeState(Auction auction, Map<Object, Object> state) {
        if (state == null || state.isEmpty()) return;
        Object current = state.get("currentBidCents");
        if (current != null) auction.setCurrentBidCents(Long.parseLong(current.toString()));
        Object leader = state.get("leader");
        if (leader != null) auction.setLeader(leader.toString());
        Object leaderName = state.get("leaderName");
        if (leaderName != null) auction.setLeaderName(leaderName.toString());
        Object status = state.get("status");
        if (status != null) auction.setStatus(status.toString());
        Object bidCount = state.get("bidCount");
        if (bidCount != null) auction.setBidCount(Long.parseLong(bidCount.toString()));
        Object paid = state.get("paid");
        if (paid != null) auction.setPaid("1".equals(paid.toString()));
        Object endsAt = state.get("endsAt");
        if (endsAt != null) auction.setEndsAt(Long.parseLong(endsAt.toString()));
    }
}
