export interface RateLimiterConfig {
  serviceUrl: string;       // URL of your Spring Boot service
  apiKey: string;           // your registered API key
  failOpen?: boolean;       // if true, allow requests when service is down (default: true)
  timeoutMs?: number;       // HTTP timeout in ms (default: 3000)
  localCacheTtlMs?: number; // how long to cache deny decisions locally (default: 1000)
}

export interface CheckResult {
  allowed: boolean;
  remaining: number;
  resetAtEpochMs: number;
  retryAfterSeconds: number;
  limit: number;
  windowSeconds: number;
}

export interface CheckOptions {
  identifier: string;  // IP, userId, or any string
  cost?: number;       // tokens to consume (default: 1)
}