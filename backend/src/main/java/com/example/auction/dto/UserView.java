package com.example.auction.dto;

import com.example.auction.model.User;

public record UserView(
        String id, String name, String email, String role,
        String avatarEmoji, String bio, long balanceCents,
        boolean banned, long createdAt) {

    public static UserView of(User u) {
        return new UserView(u.getId(), u.getName(), u.getEmail(), u.getRole(),
                u.getAvatarEmoji(), u.getBio(), u.getBalanceCents(),
                u.isBanned(), u.getCreatedAt());
    }
}
