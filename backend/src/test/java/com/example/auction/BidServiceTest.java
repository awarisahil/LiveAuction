package com.example.auction;

import com.example.auction.dto.OutboundEvent;
import com.example.auction.exception.AuctionExceptions;
import com.example.auction.model.Auction;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock DefaultRedisScript<List<Object>> placeBidScript;
    @Mock ApplicationEventPublisher publisher;
    @Mock AuctionService auctionService;

    BidService bidService;

    @BeforeEach
    void setUp() {
        bidService = new BidService(redis, placeBidScript, publisher,
                new ObjectMapper(), auctionService, 86400, 50);
    }

    @Test
    void acceptedBidBroadcastsBidAndEvent() {
        Auction auction = activeAuction();
        when(redis.execute(any(), anyList(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(1L, "bid-1"));
        when(auctionService.get("a1")).thenReturn(auction);

        var result = bidService.placeBid("a1", "alice", 6000L, "key-1");

        assertEquals("ACCEPTED", result.status());
        assertNotNull(result.bidId());

        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(publisher, times(2)).publishEvent(captor.capture());
        List<OutboundEvent> events = captor.getAllValues();
        assertEquals("/topic/auction/a1/bids", events.get(0).destination());
        assertEquals("/topic/auction/a1/events", events.get(1).destination());
        assertTrue(events.get(1).payload().contains("BID_ACCEPTED"));
        // the bid broadcast carries the accepted bid id returned to the caller
        assertTrue(events.get(0).payload().contains(result.bidId()));
    }

    @Test
    void replayedBidReturnsOriginalBidIdWithoutBroadcast() {
        when(redis.execute(any(), anyList(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(0L, "original-bid-id"));

        var result = bidService.placeBid("a1", "alice", 6000L, "key-1");

        assertEquals("REPLAYED", result.status());
        assertEquals("original-bid-id", result.bidId());
        verify(publisher, never()).publishEvent(any(OutboundEvent.class));
    }

    @Test
    void bidBelowMinimumIsRejectedWithNextMinimum() {
        when(redis.execute(any(), anyList(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(-1L, "5500"));

        var ex = assertThrows(AuctionExceptions.BidTooLowException.class,
                () -> bidService.placeBid("a1", "alice", 5000L, "key-1"));
        assertEquals(5500L, ex.getMinNextCents());
        verify(publisher, never()).publishEvent(any(OutboundEvent.class));
    }

    @Test
    void bidOnClosedAuctionIsRejected() {
        when(redis.execute(any(), anyList(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(-2L, "ENDED"));

        assertThrows(AuctionExceptions.AuctionNotActiveException.class,
                () -> bidService.placeBid("a1", "alice", 6000L, "key-1"));
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        assertThrows(AuctionExceptions.IdempotencyKeyRequiredException.class,
                () -> bidService.placeBid("a1", "alice", 6000L, "  "));
        verifyNoInteractions(redis);
    }

    private static Auction activeAuction() {
        Auction a = new Auction();
        a.setId("a1");
        a.setStatus("ACTIVE");
        a.setCurrentBidCents(6000);
        a.setLeader("alice");
        return a;
    }
}
