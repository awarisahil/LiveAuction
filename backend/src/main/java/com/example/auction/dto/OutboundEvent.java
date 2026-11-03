package com.example.auction.dto;

import org.springframework.context.ApplicationEvent;

/**
 * A payload destined for WebSocket subscribers of a given STOMP destination.
 * Published on the local event bus; WsRelay forwards it to STOMP clients and,
 * tagged with an instance id, to other backend instances via Redis pub/sub.
 */
public final class OutboundEvent extends ApplicationEvent {

    private final String destination;
    private final String payload;

    public OutboundEvent(String destination, String payload) {
        super(OutboundEvent.class);
        this.destination = destination;
        this.payload = payload;
    }

    public String destination() { return destination; }
    public String payload() { return payload; }
}
