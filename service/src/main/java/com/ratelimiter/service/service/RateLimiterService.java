package com.ratelimiter.service.service;

import com.ratelimiter.service.algorithm.RateLimitResult;
import com.ratelimiter.service.algorithm.RateLimiterStrategy;
import com.ratelimiter.service.algorithm.SlidingWindowLimiter;
import com.ratelimiter.service.algorithm.TokenBucketLimiter;
import com.ratelimiter.service.dto.CheckRequest;
import com.ratelimiter.service.dto.CheckResponse;
import com.ratelimiter.service.exception.ApiKeyNotFoundException;
import com.ratelimiter.service.model.RateLimitPolicy;
import com.ratelimiter.service.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final ApiKeyRepository   apiKeyRepository;
    private final SlidingWindowLimiter slidingWindowLimiter;
    private final TokenBucketLimiter   tokenBucketLimiter;

    public CheckResponse check(CheckRequest request) {
        // 1. Look up the policy for this API key
        RateLimitPolicy policy = apiKeyRepository
                .findByKey(request.getApiKey())
                .orElseThrow(() -> new ApiKeyNotFoundException(request.getApiKey()));

        // 2. Pick the right algorithm based on the policy
        // This is the RateLimiterStrategy interface in action
        RateLimiterStrategy strategy = resolveStrategy(policy.getAlgorithm());

        // 3. Build the full identifier key — combines apiKey + identifier
        // so different API keys don't share counters
        String identifier = request.getApiKey() + ":" + request.getIdentifier();

        // 4. Run the check
        RateLimitResult result = strategy.check(identifier, policy);

        log.info("Check — apiKey: {}, identifier: {}, allowed: {}",
                request.getApiKey(), request.getIdentifier(), result.isAllowed());

        return new CheckResponse(
                result.isAllowed(),
                result.getRemaining(),
                result.getResetAtEpochMs(),
                result.getRetryAfterSeconds(),
                policy.getLimit(),
                policy.getWindowSeconds()
        );
    }

    public String registerKey(RateLimitPolicy policy) {
        validatePolicy(policy);
        return apiKeyRepository.save(policy);
    }

    public RateLimitPolicy getPolicy(String apiKey) {
        return apiKeyRepository
                .findByKey(apiKey)
                .orElseThrow(() -> new ApiKeyNotFoundException(apiKey));
    }

    public void deleteKey(String apiKey) {
        if (!apiKeyRepository.exists(apiKey)) {
            throw new ApiKeyNotFoundException(apiKey);
        }
        apiKeyRepository.delete(apiKey);
    }

    // ── Private Helpers ───────────────────────────────────────────────────

    private RateLimiterStrategy resolveStrategy(String algorithm) {
        return switch (algorithm.toLowerCase()) {
            case "sliding_window" -> slidingWindowLimiter;
            case "token_bucket"   -> tokenBucketLimiter;
            default -> throw new IllegalArgumentException(
                    "Unknown algorithm: " + algorithm +
                            ". Valid options: sliding_window, token_bucket"
            );
        };
    }

    private void validatePolicy(RateLimitPolicy policy) {
        if (policy.getLimit() <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        if (policy.getWindowSeconds() <= 0) {
            throw new IllegalArgumentException("windowSeconds must be greater than 0");
        }
        resolveStrategy(policy.getAlgorithm()); // validates algorithm name
    }
}