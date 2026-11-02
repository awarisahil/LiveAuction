package com.example.auction.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuctionExceptions.AuctionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(AuctionExceptions.AuctionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "AUCTION_NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(AuctionExceptions.BidTooLowException.class)
    public ResponseEntity<Map<String, Object>> bidTooLow(AuctionExceptions.BidTooLowException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "error", "BID_TOO_LOW",
                        "message", ex.getMessage(),
                        "minNextCents", ex.getMinNextCents()));
    }

    @ExceptionHandler(AuctionExceptions.AuctionNotActiveException.class)
    public ResponseEntity<Map<String, Object>> notActive(AuctionExceptions.AuctionNotActiveException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "AUCTION_NOT_ACTIVE", "message", ex.getMessage()));
    }

    @ExceptionHandler(AuctionExceptions.PaymentRejectedException.class)
    public ResponseEntity<Map<String, Object>> paymentRejected(AuctionExceptions.PaymentRejectedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "PAYMENT_REJECTED", "message", ex.getMessage()));
    }

    @ExceptionHandler(AuctionExceptions.IdempotencyKeyRequiredException.class)
    public ResponseEntity<Map<String, Object>> idemKeyRequired(AuctionExceptions.IdempotencyKeyRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "IDEMPOTENCY_KEY_REQUIRED", "message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "INVALID_REQUEST", "message", "Validation failed"));
    }
}
