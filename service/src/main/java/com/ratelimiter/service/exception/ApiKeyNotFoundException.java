package com.ratelimiter.service.exception;

public class ApiKeyNotFoundException extends RuntimeException {
    public ApiKeyNotFoundException(String apiKey) {
        super("API key not found: " + apiKey);
    }
}