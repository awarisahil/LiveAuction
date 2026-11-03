package com.example.auction.exception;

public final class AuctionExceptions {

    private AuctionExceptions() {}

    public static class AuctionNotFoundException extends RuntimeException {
        public AuctionNotFoundException(String id) { super("Auction not found: " + id); }
    }

    public static class BidTooLowException extends RuntimeException {
        private final long minNextCents;
        public BidTooLowException(long minNextCents) {
            super("Bid below minimum; next bid must be at least " + minNextCents + " cents");
            this.minNextCents = minNextCents;
        }
        public long getMinNextCents() { return minNextCents; }
    }

    public static class AuctionNotActiveException extends RuntimeException {
        public AuctionNotActiveException(String status) { super("Auction is not active (status=" + status + ")"); }
    }

    public static class PaymentRejectedException extends RuntimeException {
        public PaymentRejectedException(String message) { super(message); }
    }

    public static class IdempotencyKeyRequiredException extends RuntimeException {
        public IdempotencyKeyRequiredException() { super("Idempotency-Key header is required"); }
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException() { super("Authentication required"); }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) { super(message); }
    }

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }
}
