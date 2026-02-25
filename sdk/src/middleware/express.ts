import { RateLimiter } from '../RateLimiter.js';
import { CheckOptions } from '../types/index.js';

export interface ExpressMiddlewareOptions {
  identifier?: (req: any) => string;  // custom function to extract identifier
  cost?: number;                       // tokens to consume per request
}

// Standalone middleware factory if someone wants to use it
// without calling limiter.middleware() directly
export function createExpressMiddleware(
  limiter: RateLimiter,
  options: ExpressMiddlewareOptions = {}
) {
  return async (req: any, res: any, next: any) => {
    const identifier = options.identifier
      ? options.identifier(req)
      : req.ip || req.connection?.remoteAddress || 'unknown';

    const checkOptions: CheckOptions = {
      identifier,
      cost: options.cost ?? 1,
    };

    const result = await limiter.check(checkOptions);

    // Set standard rate limit response headers
    // These are the industry standard headers clients expect
    res.setHeader('X-RateLimit-Limit',     result.limit);
    res.setHeader('X-RateLimit-Remaining', result.remaining);
    res.setHeader('X-RateLimit-Reset',     result.resetAtEpochMs);

    if (!result.allowed) {
      res.setHeader('Retry-After', result.retryAfterSeconds);
      return res.status(429).json({
        error:             'Too Many Requests',
        message:           `Rate limit exceeded. Retry in ${result.retryAfterSeconds}s.`,
        retryAfterSeconds: result.retryAfterSeconds,
      });
    }

    next();
  };
}