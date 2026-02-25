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

@Slf4j
@Component
public class TokenBucketLimiter implements RateLimiterStrategy {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> tokenBucketScript;

    public TokenBucketLimiter(StringRedisTemplate redis) {
        this.redis = redis;

        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/token_bucket.lua"))
        );
        this.tokenBucketScript.setResultType(List.class);
    }

    @Override
    public RateLimitResult check(String identifier, RateLimitPolicy policy) {
        String key = "rl:tb:" + identifier;

        long nowMs       = Instant.now().toEpochMilli();
        int  capacity    = policy.getLimit();
        // Refill rate: refill the full bucket over one window
        // e.g. limit=60, window=60s → 1 token per second
        double refillRate = (double) policy.getLimit() / policy.getWindowSeconds();

        List<Long> result = redis.execute(
                tokenBucketScript,
                List.of(key),
                String.valueOf(nowMs),
                String.valueOf(capacity),
                String.valueOf(refillRate)
        );

        boolean allowed       = result.get(0) == 1L;
        int     remaining     = result.get(1).intValue();
        long    resetAtMs     = nowMs + (policy.getWindowSeconds() * 1000L);
        int     retryAfter    = allowed ? 0 : policy.getWindowSeconds();

        log.debug("Token bucket check — key: {}, allowed: {}, tokens remaining: {}",
                key, allowed, remaining);

        return new RateLimitResult(allowed, remaining, resetAtMs, retryAfter);
    }
}