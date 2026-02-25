package com.ratelimiter.service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RateLimitPolicy {

    private int limit;          // max requests allowed
    private int windowSeconds;  // time window in seconds
    private String algorithm;   // "sliding_window" or "token_bucket"
}