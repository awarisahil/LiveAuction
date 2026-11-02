package com.example.auction.service;

import com.example.auction.dto.OutboundEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Bridges outbound events to WebSocket subscribers. Events are broadcast to
 * local STOMP clients immediately and also published on a Redis channel so
 * other backend instances (stateless API replicas behind a load balancer)
 * forward them to their own connected clients. Messages carry the originating
 * instance id to avoid duplicates.
 */
@Component
public class WsRelay implements ApplicationListener<OutboundEvent>, MessageListener {

    private final SimpMessagingTemplate messaging;
    private final StringRedisTemplate redis;
    private final ChannelTopic topic;
    private final ObjectMapper om;
    private final String instanceId = UUID.randomUUID().toString();

    public WsRelay(SimpMessagingTemplate messaging,
                   StringRedisTemplate redis,
                   ChannelTopic auctionEventsTopic,
                   ObjectMapper om) {
        this.messaging = messaging;
        this.redis = redis;
        this.topic = auctionEventsTopic;
        this.om = om;
    }

    @Override
    public void onApplicationEvent(OutboundEvent event) {
        messaging.convertAndSend(event.destination(), event.payload());
        try {
            String envelope = om.writeValueAsString(Map.of(
                    "i", instanceId,
                    "d", event.destination(),
                    "p", event.payload()));
            redis.convertAndSend(topic.getTopic(), envelope);
        } catch (Exception ignored) {
            // cross-instance fan-out is best-effort; local clients already got it
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode envelope = om.readTree(message.getBody());
            if (instanceId.equals(envelope.get("i").asText())) {
                return; // own message, already delivered locally
            }
            messaging.convertAndSend(envelope.get("d").asText(), envelope.get("p").asText());
        } catch (Exception ignored) {
            // malformed envelope; drop
        }
    }
}
