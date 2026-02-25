package com.ratelimiter.service.repository;

import com.ratelimiter.service.model.RateLimitPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ApiKeyRepository {

    // All API key policies are stored as Redis hashes under this prefix
    // e.g. "apikey:abc123" → { algorithm, limit, windowSeconds }
    private static final String KEY_PREFIX = "apikey:";

    private final StringRedisTemplate redis;

    /**
     * Register a new API key with a policy.
     * Returns the generated API key string.
     */
    public String save(RateLimitPolicy policy) {
        String apiKey = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String redisKey = KEY_PREFIX + apiKey;

        redis.opsForHash().putAll(redisKey, Map.of(
                "algorithm",     policy.getAlgorithm(),
                "limit",         String.valueOf(policy.getLimit()),
                "windowSeconds", String.valueOf(policy.getWindowSeconds())
        ));

        log.info("Registered API key: {} with policy: {}", apiKey, policy);
        return apiKey;
    }

    /**
     * Find the policy for an existing API key.
     * Returns empty if the key doesn't exist.
     */
    public Optional<RateLimitPolicy> findByKey(String apiKey) {
        String redisKey = KEY_PREFIX + apiKey;
        Map<Object, Object> fields = redis.opsForHash().entries(redisKey);

        if (fields.isEmpty()) {
            return Optional.empty();
        }

        RateLimitPolicy policy = new RateLimitPolicy(
                Integer.parseInt((String) fields.get("limit")),
                Integer.parseInt((String) fields.get("windowSeconds")),
                (String) fields.get("algorithm")
        );

        return Optional.of(policy);
    }

    /**
     * Delete an API key and all its rate limit data.
     */
    public void delete(String apiKey) {
        redis.delete(KEY_PREFIX + apiKey);
        // Also clean up any rate limit counters for this key
        redis.delete("rl:sw:" + apiKey);
        redis.delete("rl:tb:" + apiKey);
        log.info("Deleted API key: {}", apiKey);
    }

    /**
     * Check if an API key exists.
     */
    public boolean exists(String apiKey) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + apiKey));
    }
}