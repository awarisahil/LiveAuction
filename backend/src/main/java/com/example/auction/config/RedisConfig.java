package com.example.auction.config;

import com.example.auction.service.WsRelay;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

@Configuration
public class RedisConfig {

    @Bean
    public DefaultRedisScript<List<Object>> placeBidScript() {
        DefaultRedisScript<List<Object>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/place_bid.lua"));
        script.setResultType((Class<List<Object>>) (Class<?>) List.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<List<Object>> payScript() {
        DefaultRedisScript<List<Object>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/pay.lua"));
        script.setResultType((Class<List<Object>>) (Class<?>) List.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<List<Object>> claimEndScript() {
        DefaultRedisScript<List<Object>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/claim_end.lua"));
        script.setResultType((Class<List<Object>>) (Class<?>) List.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<List<Object>> setStatusScript() {
        DefaultRedisScript<List<Object>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/set_status.lua"));
        script.setResultType((Class<List<Object>>) (Class<?>) List.class);
        return script;
    }

    @Bean
    public ChannelTopic auctionEventsTopic() {
        return new ChannelTopic("auction:events");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            WsRelay wsRelay,
            ChannelTopic auctionEventsTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(wsRelay, auctionEventsTopic);
        return container;
    }
}
