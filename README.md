# Distributed Rate Limiter as a Service

A production-grade distributed rate limiting service built with Spring Boot, Redis, and an npm SDK. Prevents API abuse across multiple server instances using atomic Lua scripts for consistent enforcement.

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green?style=flat-square)
![Redis](https://img.shields.io/badge/Redis-7-red?style=flat-square)
![TypeScript](https://img.shields.io/badge/TypeScript-5-blue?style=flat-square)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square)

---

## Live Demo

Base URL: `https://distributed-rate-limiter-production-82bf.up.railway.app`

```bash

# Try it right now
curl -X POST https://distributed-rate-limiter-production-82bf.up.railway.app/api/v1/keys \
  -H "Content-Type: application/json" \
  -d '{"algorithm":"sliding_window","limit":5,"windowSeconds":30}'
```

## What This Project Does

Most rate limiters only work on a single server. When you scale to multiple instances, each server keeps its own counter — so a user can send 10× the allowed requests by hitting different servers.

This service solves that by storing all counters in a shared Redis instance. Every server checks the same counter, enforced atomically via Lua scripts. No race conditions, no inconsistency.

```
Client → Nginx (load balancer)
              ↓
    ┌─────────┴─────────┐
    │                   │
 Service 1          Service 2
    │                   │
    └─────────┬─────────┘
              ↓
           Redis
        (shared state)
```

---

## Architecture

| Component | Technology | Purpose |
|-----------|-----------|---------|
| API Service | Spring Boot 3.5 | REST endpoints, algorithm execution |
| Shared State | Redis 7 | Atomic counters, API key storage |
| Load Balancer | Nginx | Traffic distribution, first-layer limiting |
| SDK | TypeScript / npm | Client library for Node.js apps |
| Monitoring | Prometheus + Grafana | Metrics and dashboards |
| Orchestration | Docker Compose | Full stack in one command |

---

## Algorithms

### Sliding Window (Recommended)
Uses a Redis Sorted Set to store timestamped request entries. Each check atomically removes expired entries, counts remaining, and adds the new request if under the limit.

```lua
redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMs)
local count = redis.call('ZCARD', key)
if count < limit then
    redis.call('ZADD', key, now, requestId)
    return {1, limit - count - 1}
end
return {0, 0}
```

**Best for:** APIs where precise, consistent enforcement matters.

### Token Bucket
Stores token count and last refill time in a Redis Hash. Tokens refill continuously at a fixed rate — allows short bursts while controlling sustained traffic.

**Best for:** APIs where bursty traffic from legitimate users is acceptable.

---

## API Reference

All endpoints are prefixed with `/api/v1`.

### Check Rate Limit
```http
POST /api/v1/check
Content-Type: application/json

{
  "apiKey": "your-api-key",
  "identifier": "user-123",
  "cost": 1
}
```

**Response (200 — allowed):**
```json
{
  "allowed": true,
  "remaining": 4,
  "resetAtEpochMs": 1234567890000,
  "retryAfterSeconds": 0,
  "limit": 5,
  "windowSeconds": 30
}
```

**Response (429 — rate limited):**
```json
{
  "allowed": false,
  "remaining": 0,
  "retryAfterSeconds": 30,
  "limit": 5,
  "windowSeconds": 30
}
```

### Register API Key
```http
POST /api/v1/keys
Content-Type: application/json

{
  "algorithm": "sliding_window",
  "limit": 100,
  "windowSeconds": 60
}
```

### Get Policy
```http
GET /api/v1/keys/{apiKey}
```

### Delete API Key
```http
DELETE /api/v1/keys/{apiKey}
```

---

## npm SDK

### Installation
```bash
npm install @raj-deshmukh6403/rate-limiter-sdk
```

### Basic Usage
```typescript
import { RateLimiter } from '@raj-deshmukh6403/rate-limiter-sdk';

const limiter = new RateLimiter({
  serviceUrl: 'http://your-service-url',
  apiKey: 'your-api-key',
  failOpen: true,       // allow requests if service is down
  timeoutMs: 3000,      // HTTP timeout
});

const result = await limiter.check({ identifier: req.ip });

if (!result.allowed) {
  return res.status(429).json({ error: 'Rate limit exceeded' });
}
```

### Express Middleware
```typescript
import express from 'express';
import { RateLimiter } from '@raj-deshmukh6403/rate-limiter-sdk';

const app = express();
const limiter = new RateLimiter({
  serviceUrl: 'http://localhost:8080',
  apiKey: 'your-api-key',
});

// Apply to all routes
app.use(limiter.middleware());

// Apply to specific route with custom identifier
app.use('/api', limiter.middleware(req => req.headers['x-user-id'] || req.ip));
```

### SDK Features
- **Local cache** — caches deny decisions for 1 second to reduce HTTP calls
- **Circuit breaker** — fails open if service is unreachable, prevents cascading failures
- **Retry logic** — exponential backoff on transient failures
- **TypeScript** — full type definitions included
- **Dual format** — ships as both ESM and CommonJS

---

## Running Locally

### Prerequisites
- Docker Desktop
- Git

### Start Everything
```bash
git clone https://github.com/raj-deshmukh6403/distributed-rate-limiter.git
cd distributed-rate-limiter
docker compose up --build
```

This starts 6 containers:

| Service | URL |
|---------|-----|
| API (via Nginx) | http://localhost |
| Service Instance 1 | http://localhost:8080 |
| Service Instance 2 | http://localhost:8081 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |
| Redis | localhost:6379 |

### Quick Test
```bash
# Register an API key
curl -X POST http://localhost/api/v1/keys \
  -H "Content-Type: application/json" \
  -d '{"algorithm":"sliding_window","limit":5,"windowSeconds":30}'

# Use the returned apiKey to check requests
curl -X POST http://localhost/api/v1/check \
  -H "Content-Type: application/json" \
  -d '{"apiKey":"YOUR_KEY","identifier":"test-user","cost":1}'
```

Run the check 6 times — the first 5 return `"allowed": true`, the 6th returns `"allowed": false` with HTTP 429.

---

## Monitoring

Grafana dashboard at http://localhost:3000 (login: `admin` / `admin`) shows:

- **HTTP Requests per Second** — traffic across both service instances
- **Average Response Time** — latency per endpoint
- **JVM Heap Memory** — memory usage per instance
- **Live JVM Threads** — concurrency per instance

Prometheus scrapes both instances every 15 seconds via Spring Boot Actuator at `/actuator/prometheus`.

---

## Project Structure

```
distributed-rate-limiter/
├── service/                        # Spring Boot service
│   ├── src/main/java/com/ratelimiter/service/
│   │   ├── algorithm/              # Sliding window + token bucket + interface
│   │   ├── controller/             # REST endpoints
│   │   ├── dto/                    # Request/response objects
│   │   ├── exception/              # Global error handling
│   │   ├── model/                  # RateLimitPolicy
│   │   ├── repository/             # Redis API key storage
│   │   └── service/                # Business logic + algorithm routing
│   ├── src/main/resources/scripts/ # Lua scripts (sliding_window.lua, token_bucket.lua)
│   └── Dockerfile                  # Multi-stage build
├── sdk/                            # TypeScript npm package
│   └── src/
│       ├── RateLimiter.ts          # Main class
│       ├── core/                   # CircuitBreaker, LocalCache
│       ├── middleware/             # Express middleware
│       └── types/                  # TypeScript interfaces
├── nginx/
│   └── nginx.conf                  # Load balancer config
├── monitoring/
│   ├── prometheus.yml              # Scrape config
│   └── grafana/provisioning/       # Auto-provisioned datasource + dashboard
└── docker-compose.yml              # Full stack orchestration
```

---

## Design Decisions

**Why Lua scripts?** Redis executes Lua atomically — no other command can run between the steps of checking and updating a counter. Without this, two requests arriving simultaneously could both pass the check before either increments the counter.

**Why fail-open in the SDK?** Rate limiting is a best-effort protection, not a security gate. If the rate limiter goes down, it's better to allow traffic and keep your app running than to block all users. The circuit breaker prevents hammering a down service.

**Why Sorted Sets for sliding window?** Each entry is timestamped as its score. Expired entries are removed with a single `ZREMRANGEBYSCORE` command. The count of remaining entries is exact — no approximation like fixed window counters.

---

## Interview Talking Points

**On distributed systems:** "Used Redis Lua scripts for atomic operations to prevent race conditions. Both ZREMRANGEBYSCORE and ZADD execute as one indivisible operation — no other request can interleave between the check and the update."

**On algorithm trade-offs:** "Sliding window is more accurate but uses more memory — it stores one entry per request. Token bucket is more memory-efficient and burst-friendly since it only stores two values per key."

**On resilience:** "The SDK circuit breaker fails open during outages because availability trumps strict enforcement. Five consecutive failures open the circuit for 30 seconds, then one probe request tests recovery."

**On developer experience:** "Published as a typed npm package with Express middleware. Integration is three lines of code. The SDK handles caching, retries, and circuit breaking transparently."

---

## Tech Stack

- **Java 21** + **Spring Boot 3.5** — service framework
- **Spring Data Redis** + **Lettuce** — Redis client
- **Lua** — atomic scripts executed on Redis
- **TypeScript 5** + **tsup** — SDK with ESM + CJS output
- **Axios** — HTTP client in SDK
- **Docker** + **Docker Compose** — containerisation
- **Nginx** — load balancing and first-layer rate limiting
- **Prometheus** + **Grafana** — observability
- **Micrometer** — metrics bridge from Spring to Prometheus