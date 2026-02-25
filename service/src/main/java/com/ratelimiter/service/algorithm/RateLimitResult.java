package com.ratelimiter.service.algorithm;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RateLimitResult {

    private boolean allowed;      // true = allow, false = deny
    private int remaining;        // requests left in current window
    private long resetAtEpochMs;  // when the window resets (epoch milliseconds)
    private int retryAfterSeconds;// how long to wait before retrying (0 if allowed)
}