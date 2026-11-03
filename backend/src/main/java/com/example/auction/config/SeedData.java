package com.example.auction.config;

import com.example.auction.model.Auction;
import com.example.auction.service.AuctionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Seeds a demo catalog on startup: full auction JSON in the catalog key plus
 * the mirrored hot-state hash that the bidding scripts operate on.
 */
@Configuration
public class SeedData {

    @Bean
    CommandLineRunner seedAuctions(StringRedisTemplate redis, ObjectMapper om) {
        return args -> {
            if (Boolean.TRUE.equals(redis.hasKey(AuctionService.ACTIVE_INDEX))) {
                return;
            }
            long now = Instant.now().toEpochMilli();
            List<Auction> catalog = List.of(
                    auction("Vintage Film Camera", "Fully mechanical 35mm rangefinder, serviced and film-tested.", "\uD83D\uDCF7", 5000, 500, now + 3 * 60_000),
                    auction("Next-Gen Graphics Card", "Flagship GPU, still sealed in the original box.", "\uD83C\uDFAE", 25000, 1000, now + 5 * 60_000),
                    auction("Signed Championship Jersey", "Match-worn jersey signed by the entire squad.", "\uD83C\uDFC9", 10000, 750, now + 8 * 60_000));

            for (Auction a : catalog) {
                redis.opsForValue().set(AuctionService.key(a.getId()), om.writeValueAsString(a));
                redis.opsForHash().putAll(AuctionService.stateKey(a.getId()), java.util.Map.of(
                        "currentBidCents", String.valueOf(a.getCurrentBidCents()),
                        "minIncrementCents", String.valueOf(a.getMinIncrementCents()),
                        "status", a.getStatus(),
                        "bidCount", "0",
                        "endsAt", String.valueOf(a.getEndsAt())));
                redis.opsForSet().add(AuctionService.ACTIVE_INDEX, a.getId());
            }
        };
    }

    private static Auction auction(String title, String description, String emoji,
                                   long startPriceCents, long minIncrementCents, long endsAt) {
        Auction a = new Auction();
        a.setId(UUID.randomUUID().toString().substring(0, 8));
        a.setTitle(title);
        a.setDescription(description);
        a.setImageEmoji(emoji);
        a.setStartPriceCents(startPriceCents);
        a.setMinIncrementCents(minIncrementCents);
        a.setCurrentBidCents(startPriceCents);
        a.setStatus("ACTIVE");
        a.setEndsAt(endsAt);
        return a;
    }
}
