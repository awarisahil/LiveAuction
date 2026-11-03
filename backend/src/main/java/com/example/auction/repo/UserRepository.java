package com.example.auction.repo;

import com.example.auction.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class UserRepository {

    public static final String INDEX = "user:index";

    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public UserRepository(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis;
        this.om = om;
    }

    public static String key(String id) { return "user:" + id; }
    public static String emailKey(String email) { return "user:email:" + email.toLowerCase(); }
    public static String bidsKey(String id) { return "user:" + id + ":bids"; }
    public static String paymentsKey(String id) { return "user:" + id + ":payments"; }
    public static String watchKey(String id) { return "user:" + id + ":watch"; }

    public Optional<User> get(String id) {
        String json = redis.opsForValue().get(key(id));
        return parse(json);
    }

    public Optional<User> byEmail(String email) {
        String id = redis.opsForValue().get(emailKey(email));
        if (id == null) return Optional.empty();
        return get(id);
    }

    /** Creates or updates the user plus the email index. */
    public void save(User user) {
        try {
            redis.opsForValue().set(key(user.getId()), om.writeValueAsString(user));
            redis.opsForValue().set(emailKey(user.getEmail()), user.getId());
            redis.opsForSet().add(INDEX, user.getId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save user " + user.getId(), e);
        }
    }

    public List<User> listAll() {
        Set<String> ids = redis.opsForSet().members(INDEX);
        List<User> users = new ArrayList<>();
        if (ids == null) return users;
        for (String id : ids) {
            get(id).ifPresent(users::add);
        }
        users.sort((a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt()));
        return users;
    }

    public long count() {
        Long n = redis.opsForSet().size(INDEX);
        return n == null ? 0 : n;
    }

    private Optional<User> parse(String json) {
        if (json == null) return Optional.empty();
        try {
            return Optional.of(om.readValue(json, User.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
