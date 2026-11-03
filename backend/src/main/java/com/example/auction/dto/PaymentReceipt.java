package com.example.auction.dto;

public record PaymentReceipt(String id, String auctionId, String payer,
                             long amountCents, long feeCents, long paidAt) {
}
