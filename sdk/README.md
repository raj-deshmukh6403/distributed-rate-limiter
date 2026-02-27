# @rajvardhan6403/rate-limiter-sdk

TypeScript SDK for [Distributed Rate Limiter as a Service](https://github.com/raj-deshmukh6403/distributed-rate-limiter) — a production-grade distributed rate limiting service built with Spring Boot and Redis.

## Installation

```bash
npm install @rajvardhan6403/rate-limiter-sdk
```

## Quick Start

```typescript
import { RateLimiter } from '@rajvardhan6403/rate-limiter-sdk';

const limiter = new RateLimiter({
  serviceUrl: 'https://distributed-rate-limiter-production-82bf.up.railway.app',
  apiKey: 'your-api-key',
});

const result = await limiter.check({ identifier: 'user-123' });

if (!result.allowed) {
  console.log('Rate limit exceeded. Retry after:', result.retryAfterSeconds, 'seconds');
} else {
  console.log('Request allowed. Remaining:', result.remaining);
}
```

## Getting an API Key

Register an API key from the live service:

```bash
curl -X POST https://distributed-rate-limiter-production-82bf.up.railway.app/api/v1/keys \
  -H "Content-Type: application/json" \
  -d '{"algorithm":"sliding_window","limit":100,"windowSeconds":60}'
```

Response:
```json
{
  "apiKey": "your-api-key",
  "message": "API key registered successfully"
}
```

## Usage

### Basic Check

```typescript
const result = await limiter.check({
  identifier: 'user-123',  // identifies who is making the request
  cost: 1                   // how many tokens to consume (default: 1)
});

console.log(result.allowed);          // true or false
console.log(result.remaining);        // how many requests left
console.log(result.retryAfterSeconds) // seconds to wait if denied
```

### Express Middleware

Add rate limiting to your Express app in 3 lines:

```typescript
import express from 'express';
import { RateLimiter } from '@rajvardhan6403/rate-limiter-sdk';

const app = express();

const limiter = new RateLimiter({
  serviceUrl: 'https://distributed-rate-limiter-production-82bf.up.railway.app',
  apiKey: 'your-api-key',
});

// Apply to all routes — identifies users by IP address
app.use(limiter.middleware());

// Apply to specific route with custom identifier
app.use('/api', limiter.middleware(req => req.headers['x-user-id'] as string || req.ip));

app.get('/hello', (req, res) => {
  res.json({ message: 'Hello World' });
});

app.listen(3000);
```

The middleware automatically sets these response headers:
- `X-RateLimit-Limit` — total requests allowed
- `X-RateLimit-Remaining` — requests remaining
- `X-RateLimit-Reset` — when the window resets
- `Retry-After` — seconds to wait (only on 429 responses)

### Configuration Options

```typescript
const limiter = new RateLimiter({
  serviceUrl: 'https://your-service-url',  // required
  apiKey: 'your-api-key',                  // required
  failOpen: true,                          // allow requests if service is down (default: true)
  timeoutMs: 3000,                         // HTTP timeout in ms (default: 3000)
  localCacheTtlMs: 1000,                   // cache deny decisions for 1s (default: 1000)
});
```

## Algorithms

When registering your API key you choose between two algorithms:

### Sliding Window (Recommended)
```bash
curl -X POST .../api/v1/keys \
  -d '{"algorithm":"sliding_window","limit":100,"windowSeconds":60}'
```
Precise per-user enforcement. Counts requests in a rolling time window. Best for most APIs.

### Token Bucket
```bash
curl -X POST .../api/v1/keys \
  -d '{"algorithm":"token_bucket","limit":100,"windowSeconds":60}'
```
Allows short bursts while controlling sustained traffic. Best when legitimate users need occasional spikes.

## Features

- **Distributed** — works correctly across multiple server instances via shared Redis
- **Circuit breaker** — fails open if the rate limiter service goes down, keeping your app running
- **Local cache** — caches deny decisions for 1 second to reduce HTTP calls
- **TypeScript** — full type definitions included
- **Universal** — ships as both ESM and CommonJS

## Response Object

```typescript
interface RateLimitResult {
  allowed: boolean;           // whether the request is allowed
  remaining: number;          // requests remaining in current window
  limit: number;              // total limit configured
  windowSeconds: number;      // window size in seconds
  resetAtEpochMs: number;     // when the window resets (epoch ms)
  retryAfterSeconds: number;  // seconds to wait before retrying (0 if allowed)
}
```

## Self Hosting

You can run your own instance of the rate limiter service using Docker:

```bash
git clone https://github.com/raj-deshmukh6403/distributed-rate-limiter
cd distributed-rate-limiter
docker compose up --build
```

Then point the SDK to your local instance:

```typescript
const limiter = new RateLimiter({
  serviceUrl: 'http://localhost',
  apiKey: 'your-api-key',
});
```

## License

MIT