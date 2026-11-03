package com.example.auction.config;

import com.example.auction.model.Auction;
import com.example.auction.model.User;
import com.example.auction.repo.UserRepository;
import com.example.auction.service.AuctionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.List;

/**
 * Seeds demo accounts and a demo catalog on first boot (or after a flush):
 * an admin, a seller with listings across categories, and a buyer with a
 * funded wallet.
 */
@Configuration
public class SeedData {

    @Bean
    CommandLineRunner seed(StringRedisTemplate redis, ObjectMapper om,
                           AuctionService auctionService) {
        return args -> {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

            String adminId = null;
            String sellerId = null;
            if (!Boolean.TRUE.equals(redis.hasKey(UserRepository.INDEX))) {
                adminId = seedUser(redis, om, encoder, "Ava Admin", "admin@demo.com", "admin123", "ADMIN", "\uD83C\uDF9F\uFE0F", 0);
                sellerId = seedUser(redis, om, encoder, "Sam Seller", "seller@demo.com", "seller123", "SELLER", "\uD83D\uDCBC", 0);
                seedUser(redis, om, encoder, "Bianca Buyer", "buyer@demo.com", "buyer123", "BUYER", "\uD83D\uDCB0", 5_000_000);
            } else {
                adminId = users.get("admin@demo.com").map(User::getId).orElse(null);
                sellerId = users.get("seller@demo.com").map(User::getId).orElse(null);
            }

            if (!Boolean.TRUE.equals(redis.hasKey(AuctionService.ACTIVE_INDEX)) && sellerId != null) {
                long now = Instant.now().toEpochMilli();
                List<Auction> catalog = List.of(
                        auction(sellerId, "Sam Seller", "Vintage Film Camera", "Fully mechanical 35mm rangefinder, serviced and film-tested.", "\uD83D\uDCF7", "COLLECTIBLES", 5000, 500, now + 2 * 60_000),
                        auction(sellerId, "Sam Seller", "Next-Gen Graphics Card", "Flagship GPU, still sealed in the original box.", "\uD83C\uDFAE", "ELECTRONICS", 25000, 1000, now + 4 * 60_000),
                        auction(sellerId, "Sam Seller", "Signed Championship Jersey", "Match-worn jersey signed by the entire squad.", "\uD83C\uDFC9", "SPORTS", 10000, 750, now + 7 * 60_000),
                        auction(sellerId, "Sam Seller", "Handmade Ceramic Vase", "Wheel-thrown stoneware with a celadon glaze. One of a kind.", "\uD83C\uDF3F", "HOME", 8000, 400, now + 12 * 60_000),
                        auction(sellerId, "Sam Seller", "Original Sci-Fi Poster 1979", "Linen-backed one-sheet, archival quality.", "\uD83C\uDFAC", "ART", 15000, 1000, now + 20 * 60_000),
                        auction(sellerId, "Sam Seller", "Restored Road Bicycle", "Rebuilt drivetrain, new brake cables, rides like new.", "\uD83D\uDEB4", "VEHICLES", 30000, 1500, now + 30 * 60_000));

                for (Auction a : catalog) {
                    auctionService.persistNew(a);
                }
            }
        };
    }

    private final com.example.auction.repo.UserRepository users;

    public SeedData(com.example.auction.repo.UserRepository users) {
        this.users = users;
    }

    private static String seedUser(StringRedisTemplate redis, ObjectMapper om,
                                   BCryptPasswordEncoder encoder, String name, String email,
                                   String password, String role, String avatar, long balanceCents) {
        try {
            User user = new User();
            user.setId(java.util.UUID.randomUUID().toString().substring(0, 12));
            user.setName(name);
            user.setEmail(email);
            user.setPasswordHash(encoder.encode(password));
            user.setRole(role);
            user.setAvatarEmoji(avatar);
            user.setBalanceCents(balanceCents);
            user.setCreatedAt(Instant.now().toEpochMilli());
            redis.opsForValue().set(UserRepository.key(user.getId()), om.writeValueAsString(user));
            redis.opsForValue().set(UserRepository.emailKey(email), user.getId());
            redis.opsForSet().add(UserRepository.INDEX, user.getId());
            redis.opsForHash().put(UserRepository.walletKey(user.getId()), "balanceCents", String.valueOf(balanceCents));
            return user.getId();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed user " + email, e);
        }
    }

    private static Auction auction(String sellerId, String sellerName, String title, String description,
                                   String emoji, String category, long startPriceCents,
                                   long minIncrementCents, long endsAt) {
        Auction a = new Auction();
        a.setId(java.util.UUID.randomUUID().toString().substring(0, 8));
        a.setTitle(title);
        a.setDescription(description);
        a.setImageEmoji(emoji);
        a.setCategory(category);
        a.setSellerId(sellerId);
        a.setSellerName(sellerName);
        a.setStartPriceCents(startPriceCents);
        a.setMinIncrementCents(minIncrementCents);
        a.setCurrentBidCents(startPriceCents);
        a.setStatus("ACTIVE");
        a.setEndsAt(endsAt);
        return a;
    }
}
