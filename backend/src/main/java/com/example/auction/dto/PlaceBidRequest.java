package com.example.auction.dto;

import jakarta.validation.constraints.Positive;

public record PlaceBidRequest(@Positive long amountCents) {
}
