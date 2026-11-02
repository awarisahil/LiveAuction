package com.example.auction.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Auction {

    private String id;
    private String title;
    private String description;
    private String imageEmoji;
    private long startPriceCents;
    private long minIncrementCents;
    private long currentBidCents;
    private String leader;
    private String status = "ACTIVE";
    private long startsAt;
    private long endsAt;
    private long bidCount;
    private boolean paid;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageEmoji() { return imageEmoji; }
    public void setImageEmoji(String imageEmoji) { this.imageEmoji = imageEmoji; }
    public long getStartPriceCents() { return startPriceCents; }
    public void setStartPriceCents(long startPriceCents) { this.startPriceCents = startPriceCents; }
    public long getMinIncrementCents() { return minIncrementCents; }
    public void setMinIncrementCents(long minIncrementCents) { this.minIncrementCents = minIncrementCents; }
    public long getCurrentBidCents() { return currentBidCents; }
    public void setCurrentBidCents(long currentBidCents) { this.currentBidCents = currentBidCents; }
    public String getLeader() { return leader; }
    public void setLeader(String leader) { this.leader = leader; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getStartsAt() { return startsAt; }
    public void setStartsAt(long startsAt) { this.startsAt = startsAt; }
    public long getEndsAt() { return endsAt; }
    public void setEndsAt(long endsAt) { this.endsAt = endsAt; }
    public long getBidCount() { return bidCount; }
    public void setBidCount(long bidCount) { this.bidCount = bidCount; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
}
