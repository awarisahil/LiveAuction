package com.example.auction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateAuctionRequest(
        @NotBlank @Size(min = 3, max = 90) String title,
        @Size(max = 600) String description,
        @NotBlank String category,
        @Positive long startPriceCents,
        @Positive long minIncrementCents,
        @Min(1) @Max(10080) int durationMinutes,
        String imageEmoji,
        @Size(max = 500) String imageUrl
) {
}
