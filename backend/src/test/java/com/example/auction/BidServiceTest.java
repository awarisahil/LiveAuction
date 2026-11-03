package com.example.auction;

import com.example.auction.auth.AuthUser;
import com.example.auction.dto.OutboundEvent;
import com.example.auction.exception.AuctionExceptions;
import com.example.auction.model.Auction;
import com.example.auction.model.User;
import com.example.auction.repo.UserRepository;
import com.example.auction.service.AuctionService;
import com.example.auction.service.BidService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    static final AuthUser ALICE = new AuthUser("u1", "alice", "alice@demo.com", "BUYER");

    @Mock StringRedisTemplate redis;
    @Mock ListOperations<String, String> listOps;
    @Mock DefaultRedisScript<List<Object>> placeBidScript;
    @Mock ApplicationEventPublisher publisher;
    @Mock AuctionService auctionService;
    @Mock UserRepository users;

    BidService bidService;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForList()).thenReturn(listOps);
        bidService = new BidService(redis, placeBidScript, publisher,
                new ObjectMapper(), auctionService, users, 86400, 50, 30000);
    }

    @Test
    void acceptedBidBroadcastsBidAndEvent() {
        Auction auction = activeAuction("seller-9");
        when(users.get("u1")).thenReturn(Optional.of(activeUser()));
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, "bid-1"));
        when(auctionService.get("a1")).thenReturn(auction);

        var result = bidService.placeBid("a1", 6000L, ALICE, "key-1");

        assertEquals("ACCEPTED", result.status());
        assertNotNull(result.bidId());

        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(publisher, times(2)).publishEvent(captor.capture());
        List<OutboundEvent> events = captor.getAllValues();
        assertEquals("/topic/auction/a1/bids", events.get(0).destination());
        assertEquals("/topic/auction/a1/events", events.get(1).destination());
        assertTrue(events.get(1).payload().contains("BID_ACCEPTED"));
    }

    @Test
    void replayedBidReturnsOriginalBidIdWithoutBroadcast() {
        when(users.get("u1")).thenReturn(Optional.of(activeUser()));
        when(auctionService.get("a1")).thenReturn(activeAuction("seller-9"));
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, "original-bid-id"));

        var result = bidService.placeBid("a1", 6000L, ALICE, "key-1");

        assertEquals("REPLAYED", result.status());
        assertEquals("original-bid-id", result.bidId());
        verify(publisher, never()).publishEvent(any(OutboundEvent.class));
    }

    @Test
    void bidBelowMinimumIsRejectedWithNextMinimum() {
        when(users.get("u1")).thenReturn(Optional.of(activeUser()));
        when(auctionService.get("a1")).thenReturn(activeAuction("seller-9"));
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(-1L, "5500"));

        var ex = assertThrows(AuctionExceptions.BidTooLowException.class,
                () -> bidService.placeBid("a1", 5000L, ALICE, "key-1"));
        assertEquals(5500L, ex.getMinNextCents());
    }

    @Test
    void bidOnClosedAuctionIsRejected() {
        when(users.get("u1")).thenReturn(Optional.of(activeUser()));
        when(auctionService.get("a1")).thenReturn(activeAuction("seller-9"));
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(-2L, "ENDED"));

        assertThrows(AuctionExceptions.AuctionNotActiveException.class,
                () -> bidService.placeBid("a1", 6000L, ALICE, "key-1"));
    }

    @Test
    void bannedUserCannotBid() {
        User banned = activeUser();
        banned.setBanned(true);
        when(users.get("u1")).thenReturn(Optional.of(banned));

        assertThrows(AuctionExceptions.ForbiddenException.class,
                () -> bidService.placeBid("a1", 6000L, ALICE, "key-1"));
        verifyNoInteractions(redis);
    }

    @Test
    void sellerCannotBidOnOwnListing() {
        AuthUser seller = new AuthUser("seller-9", "sam", "sam@demo.com", "SELLER");
        when(users.get("seller-9")).thenReturn(Optional.of(activeUser()));
        when(auctionService.get("a1")).thenReturn(activeAuction("seller-9"));

        assertThrows(AuctionExceptions.ForbiddenException.class,
                () -> bidService.placeBid("a1", 6000L, seller, "key-1"));
        verifyNoInteractions(redis);
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        assertThrows(AuctionExceptions.IdempotencyKeyRequiredException.class,
                () -> bidService.placeBid("a1", 6000L, ALICE, "  "));
        verifyNoInteractions(redis);
    }

    private static User activeUser() {
        User u = new User();
        u.setId("u1");
        u.setName("alice");
        u.setRole("BUYER");
        return u;
    }

    private static Auction activeAuction(String sellerId) {
        Auction a = new Auction();
        a.setId("a1");
        a.setStatus("ACTIVE");
        a.setSellerId(sellerId);
        a.setCurrentBidCents(6000);
        return a;
    }
}
