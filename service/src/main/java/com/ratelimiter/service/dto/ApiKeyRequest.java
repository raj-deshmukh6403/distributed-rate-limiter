package com.ratelimiter.service.dto;

import lombok.Data;

@Data
public class ApiKeyRequest {
    private String algorithm;    // "sliding_window" or "token_bucket"
    private int limit;           // max requests allowed
    private int windowSeconds;   // time window
}