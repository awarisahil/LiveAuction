package com.example.auction;

import com.example.auction.auth.AuthUser;
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

    static final AuthUser ALICE = new AuthUser("u1", "alice", "alice@demo.com", "BUYER");

    @Mock StringRedisTemplate redis;
    @Mock DefaultRedisScript<List<Object>> payScript;
    @Mock ApplicationEventPublisher publisher;
    @Mock AuctionService auctionService;

    PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(redis, payScript, publisher,
                new ObjectMapper(), auctionService, 86400, 500);
    }

    @Test
    void winnerPaymentSettlesForWinningAmount() {
        when(auctionService.get("a1")).thenReturn(endedAuction("u1", "seller-9"));
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, "receipt-1"));
        when(auctionService.paymentJson("a1"))
                .thenReturn("{\"id\":\"receipt-1\",\"payer\":\"u1\",\"amountCents\":7000,\"feeCents\":350,\"paidAt\":123}");

        var receipt = paymentService.pay("a1", ALICE, "pay-key-1");

        assertEquals("receipt-1", receipt.id());
        assertEquals("u1", receipt.payer());
        assertEquals(7000L, receipt.amountCents());
        assertEquals(350L, receipt.feeCents());
    }

    @Test
    void replayReturnsStoredReceipt() {
        when(auctionService.get("a1")).thenReturn(endedAuction("u1", "seller-9"));
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, "original-receipt"));
        when(auctionService.paymentJson("a1"))
                .thenReturn("{\"id\":\"original-receipt\",\"payer\":\"u1\",\"amountCents\":7000,\"feeCents\":350,\"paidAt\":123}");

        var receipt = paymentService.pay("a1", ALICE, "pay-key-1");

        assertEquals("original-receipt", receipt.id());
        assertEquals(7000L, receipt.amountCents());
    }

    @Test
    void nonWinnerCannotPay() {
        when(auctionService.get("a1")).thenReturn(endedAuction("u2", "seller-9"));

        assertThrows(AuctionExceptions.PaymentRejectedException.class,
                () -> paymentService.pay("a1", ALICE, "pay-key-1"));
        verifyNoInteractions(redis);
    }

    @Test
    void insufficientFundsIsRejected() {
        when(auctionService.get("a1")).thenReturn(endedAuction("u1", "seller-9"));
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(-6L, "2000"));

        var ex = assertThrows(AuctionExceptions.PaymentRejectedException.class,
                () -> paymentService.pay("a1", ALICE, "pay-key-1"));
        assertTrue(ex.getMessage().contains("Insufficient"));
    }

    @Test
    void doubleSettlementIsRejected() {
        when(auctionService.get("a1")).thenReturn(endedAuction("u1", "seller-9"));
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(-5L, "ALREADY_PAID"));

        assertThrows(AuctionExceptions.PaymentRejectedException.class,
                () -> paymentService.pay("a1", ALICE, "another-key"));
    }

    private static Auction endedAuction(String leaderId, String sellerId) {
        Auction a = new Auction();
        a.setId("a1");
        a.setStatus("ENDED");
        a.setLeader(leaderId);
        a.setSellerId(sellerId);
        a.setCurrentBidCents(7000);
        return a;
    }
}
