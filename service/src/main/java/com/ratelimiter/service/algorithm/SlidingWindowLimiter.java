package com.ratelimiter.service.algorithm;

import com.ratelimiter.service.model.RateLimitPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class SlidingWindowLimiter implements RateLimiterStrategy {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> slidingWindowScript;

    public SlidingWindowLimiter(StringRedisTemplate redis) {
        this.redis = redis;

        // Load the Lua script from resources/scripts/sliding_window.lua
        // Spring caches the SHA1 hash and uses EVALSHA on subsequent calls
        // which is faster than sending the full script every time
        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/sliding_window.lua"))
        );
        this.slidingWindowScript.setResultType(List.class);
    }

    @Override
    public RateLimitResult check(String identifier, RateLimitPolicy policy) {
        // Build the Redis key — namespace it so keys don't collide
        String key = "rl:sw:" + identifier;

        long nowMs       = Instant.now().toEpochMilli();
        long windowMs    = (long) policy.getWindowSeconds() * 1000;
        String requestId = UUID.randomUUID().toString();

        // Execute the Lua script atomically on Redis
        List<Long> result = redis.execute(
                slidingWindowScript,
                List.of(key),              // KEYS
                String.valueOf(nowMs),     // ARGV[1] — current time
                String.valueOf(windowMs),  // ARGV[2] — window size
                String.valueOf(policy.getLimit()), // ARGV[3] — limit
                requestId                  // ARGV[4] — unique request ID
        );

        boolean allowed   = result.get(0) == 1L;
        int remaining     = result.get(1).intValue();
        long resetAtMs    = nowMs + windowMs;
        int retryAfter    = allowed ? 0 : policy.getWindowSeconds();

        log.debug("Rate limit check — key: {}, allowed: {}, remaining: {}",
                key, allowed, remaining);

        return new RateLimitResult(allowed, remaining, resetAtMs, retryAfter);
    }
}