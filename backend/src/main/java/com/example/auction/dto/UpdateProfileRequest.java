package com.example.auction.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 40) String name,
        @Size(max = 8) String avatarEmoji,
        @Size(max = 300) String bio
) {
}
