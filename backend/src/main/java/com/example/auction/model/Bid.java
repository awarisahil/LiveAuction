package com.example.auction.model;

public record Bid(String id, String auctionId, String bidderId, String bidder,
                  long amountCents, long at) {
}
