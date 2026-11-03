package com.example.auction.dto;

import jakarta.validation.constraints.Positive;

public record DepositRequest(@Positive long amountCents) {
}
