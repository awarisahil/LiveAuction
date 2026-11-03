package com.example.auction;

import com.example.auction.exception.AuctionExceptions;
import com.example.auction.model.Auction;
import com.example.auction.service.AuctionService;
import com.example.auction.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class PaymentServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock DefaultRedisScript<List<Object>> payScript;
    @Mock ApplicationEventPublisher publisher;
    @Mock AuctionService auctionService;

    PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(redis, payScript, publisher,
                new ObjectMapper(), auctionService, 86400);
    }

    @Test
    void winnerPaymentSettlesForWinningAmount() {
        Auction ended = endedAuction("alice");
        when(redis.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(1L, "receipt-1"));
        when(auctionService.get("a1")).thenReturn(ended);

        var receipt = paymentService.pay("a1", "alice", "pay-key-1");

        assertNotNull(receipt.id());
        assertEquals("alice", receipt.payer());
        assertEquals(7000L, receipt.amountCents());
    }

    @Test
    void replayReturnsStoredReceipt() {
        when(redis.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(0L, "original-receipt"));
        when(auctionService.paymentJson("a1"))
                .thenReturn("{\"id\":\"original-receipt\",\"payer\":\"alice\",\"amountCents\":7000,\"paidAt\":123}");

        var receipt = paymentService.pay("a1", "alice", "pay-key-1");

        assertEquals("original-receipt", receipt.id());
        assertEquals(7000L, receipt.amountCents());
    }

    @Test
    void nonWinnerCannotPay() {
        when(redis.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(-3L, "alice"));

        var ex = assertThrows(AuctionExceptions.PaymentRejectedException.class,
                () -> paymentService.pay("a1", "mallory", "pay-key-1"));
        assertTrue(ex.getMessage().contains("alice"));
    }

    @Test
    void doubleSettlementIsRejected() {
        when(redis.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(-5L, "ALREADY_PAID"));

        assertThrows(AuctionExceptions.PaymentRejectedException.class,
                () -> paymentService.pay("a1", "alice", "another-key"));
    }

    private static Auction endedAuction(String leader) {
        Auction a = new Auction();
        a.setId("a1");
        a.setStatus("ENDED");
        a.setLeader(leader);
        a.setCurrentBidCents(7000);
        return a;
    }
}
