import axios, { AxiosInstance } from 'axios';
import { RateLimiterConfig, CheckResult, CheckOptions } from './types/index.js';
import { LocalCache } from './core/LocalCache.js';
import { CircuitBreaker } from './core/CircuitBreaker.js';

export class RateLimiter {
  private readonly config: Required<RateLimiterConfig>;
  private readonly client: AxiosInstance;
  private readonly cache: LocalCache;
  private readonly breaker: CircuitBreaker;

  constructor(config: RateLimiterConfig) {
    // Apply defaults
    this.config = {
      failOpen:        true,
      timeoutMs:       3000,
      localCacheTtlMs: 1000,
      ...config,
    };

    this.client = axios.create({
      baseURL: this.config.serviceUrl,
      timeout: this.config.timeoutMs,
      headers: { 'Content-Type': 'application/json' },
      // Tell axios to not throw on 429 — it's a valid rate limit response
      validateStatus: (status) => status < 500,
    });

    this.cache   = new LocalCache(this.config.localCacheTtlMs);
    this.breaker = new CircuitBreaker();
  }

  // Core check method — returns allow/deny decision
  async check(options: CheckOptions): Promise<CheckResult> {
    const cacheKey = `${this.config.apiKey}:${options.identifier}`;

    // 1. Check local cache first — if we cached a deny, return it immediately
    const cached = this.cache.get(cacheKey);
    if (cached !== null && !cached) {
      return this.buildDeniedResult();
    }

    // 2. Check circuit breaker — if service is down, fail open or closed
    if (!this.breaker.isAllowed()) {
      console.warn('[RateLimiter] Circuit breaker open — failing ' +
        (this.config.failOpen ? 'open (allowing)' : 'closed (denying)'));
      return this.buildFailResult(this.config.failOpen);
    }

    // 3. Call the rate limiter service
    try {
      const response = await this.client.post<CheckResult>('/api/v1/check', {
        apiKey:     this.config.apiKey,
        identifier: options.identifier,
        cost:       options.cost ?? 1,
      });

      this.breaker.onSuccess();
      const result = response.data;

      // Cache deny decisions locally to reduce load
      if (!result.allowed) {
        this.cache.set(cacheKey, false);
      }

      return result;

    } catch (error) {
      this.breaker.onFailure();

      // Service is down — fail open (allow) or closed (deny) based on config
      console.error('[RateLimiter] Service unreachable:', error);
      return this.buildFailResult(this.config.failOpen);
    }
  }

  // Express.js middleware factory
  middleware(getIdentifier?: (req: any) => string) {
    return async (req: any, res: any, next: any) => {
      const identifier = getIdentifier
        ? getIdentifier(req)
        : req.ip || req.connection.remoteAddress || 'unknown';

      const result = await this.check({ identifier });

      // Set standard rate limit headers
      res.setHeader('X-RateLimit-Limit',     result.limit);
      res.setHeader('X-RateLimit-Remaining', result.remaining);
      res.setHeader('X-RateLimit-Reset',     result.resetAtEpochMs);

      if (!result.allowed) {
        res.setHeader('Retry-After', result.retryAfterSeconds);
        return res.status(429).json({
          error:   'Too Many Requests',
          message: `Rate limit exceeded. Try again in ${result.retryAfterSeconds} seconds.`,
          retryAfterSeconds: result.retryAfterSeconds,
        });
      }

      next();
    };
  }

  private buildDeniedResult(): CheckResult {
    return {
      allowed: false, remaining: 0,
      resetAtEpochMs: Date.now() + 30000,
      retryAfterSeconds: 30, limit: 0, windowSeconds: 0,
    };
  }

  private buildFailResult(allow: boolean): CheckResult {
    return {
      allowed: allow, remaining: allow ? 999 : 0,
      resetAtEpochMs: Date.now() + 30000,
      retryAfterSeconds: allow ? 0 : 30, limit: 0, windowSeconds: 0,
    };
  }
}