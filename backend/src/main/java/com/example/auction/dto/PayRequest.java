package com.example.auction.dto;

import jakarta.validation.constraints.NotBlank;

public record PayRequest(@NotBlank String payer) {
}
