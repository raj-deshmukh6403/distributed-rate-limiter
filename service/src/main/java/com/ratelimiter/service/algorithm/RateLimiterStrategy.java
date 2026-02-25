package com.ratelimiter.service.algorithm;

import com.ratelimiter.service.model.RateLimitPolicy;

public interface RateLimiterStrategy {

    /**
     * Check if a request should be allowed.
     *
     * @param key    unique identifier (e.g. "user:123" or "ip:192.168.1.1")
     * @param policy the rate limit rules to apply
     * @return result containing allow/deny decision and remaining quota
     */
    RateLimitResult check(String key, RateLimitPolicy policy);
}