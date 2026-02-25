package com.ratelimiter.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckResponse {
    private boolean allowed;
    private int remaining;
    private long resetAtEpochMs;
    private int retryAfterSeconds;
    private int limit;
    private int windowSeconds;
}