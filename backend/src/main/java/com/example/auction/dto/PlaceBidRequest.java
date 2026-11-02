package com.example.auction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PlaceBidRequest(
        @NotBlank String bidder,
        @Positive long amountCents
) {
}
