package com.example.auction.dto;

public record BidResult(String status, String bidId) {
    public static BidResult accepted(String bidId) { return new BidResult("ACCEPTED", bidId); }
    public static BidResult replayed(String bidId) { return new BidResult("REPLAYED", bidId); }
}
