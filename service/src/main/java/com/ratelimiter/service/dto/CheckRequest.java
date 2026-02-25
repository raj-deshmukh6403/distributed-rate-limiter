package com.ratelimiter.service.dto;

import lombok.Data;

@Data
public class CheckRequest {
    private String apiKey;      // which registered key to use
    private String identifier;  // who is making the request (IP, userId, etc.)
    private int cost;           // how many tokens to consume (default 1)
}