package com.ratelimiter.service.controller;

import com.ratelimiter.service.dto.ApiKeyRequest;
import com.ratelimiter.service.dto.CheckRequest;
import com.ratelimiter.service.dto.CheckResponse;
import com.ratelimiter.service.model.RateLimitPolicy;
import com.ratelimiter.service.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RateLimitController {

    private final RateLimiterService rateLimiterService;

    /**
     * POST /api/v1/check
     * Core endpoint — check if a request should be allowed.
     * Returns 200 if allowed, 429 if rate limited.
     */
    @PostMapping("/check")
    public ResponseEntity<CheckResponse> check(@RequestBody CheckRequest request) {
        CheckResponse response = rateLimiterService.check(request);

        // Return 429 Too Many Requests if denied
        HttpStatus status = response.isAllowed()
                ? HttpStatus.OK
                : HttpStatus.TOO_MANY_REQUESTS;

        return ResponseEntity.status(status).body(response);
    }

    /**
     * POST /api/v1/keys
     * Register a new API key with a rate limit policy.
     */
    @PostMapping("/keys")
    public ResponseEntity<Map<String, String>> registerKey(
            @RequestBody ApiKeyRequest request) {

        RateLimitPolicy policy = new RateLimitPolicy(
                request.getLimit(),
                request.getWindowSeconds(),
                request.getAlgorithm()
        );

        String apiKey = rateLimiterService.registerKey(policy);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "apiKey",  apiKey,
                "message", "API key registered successfully"
        ));
    }

    /**
     * GET /api/v1/keys/{apiKey}
     * Get the policy for an existing API key.
     */
    @GetMapping("/keys/{apiKey}")
    public ResponseEntity<RateLimitPolicy> getKey(@PathVariable String apiKey) {
        RateLimitPolicy policy = rateLimiterService.getPolicy(apiKey);
        return ResponseEntity.ok(policy);
    }

    /**
     * DELETE /api/v1/keys/{apiKey}
     * Delete an API key and all its data.
     */
    @DeleteMapping("/keys/{apiKey}")
    public ResponseEntity<Map<String, String>> deleteKey(@PathVariable String apiKey) {
        rateLimiterService.deleteKey(apiKey);
        return ResponseEntity.ok(Map.of("message", "API key deleted successfully"));
    }
}