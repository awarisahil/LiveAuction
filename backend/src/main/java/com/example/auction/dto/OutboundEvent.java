package com.example.auction.dto;

/**
 * A payload destined for WebSocket subscribers of a given STOMP destination.
 * Published on the local event bus; WsRelay forwards it to STOMP clients and,
 * tagged with an instance id, to other backend instances via Redis pub/sub.
 */
public record OutboundEvent(String destination, String payload) {
}
