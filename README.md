# Distributed Rate Limiter as a Service

A production-grade distributed rate limiting service built with Spring Boot, Redis, and an npm SDK. Prevents API abuse across multiple server instances using atomic Lua scripts for consistent enforcement.

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green?style=flat-square)
![Redis](https://img.shields.io/badge/Redis-7-red?style=flat-square)
![TypeScript](https://img.shields.io/badge/TypeScript-5-blue?style=flat-square)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square)
![Railway](https://img.shields.io/badge/Deployed-Railway-purple?style=flat-square)
![npm](https://img.shields.io/badge/npm-@rajvardhan6403%2Frate--limiter--sdk-red?style=flat-square)

⭐ If you find this project useful, please consider giving it a star — it helps others discover it!

---

## Live Demo

**Base URL:** `https://distributed-rate-limiter-production-82bf.up.railway.app`

Try it right now — no setup needed:

```bash
# Step 1 — Register an API key
curl -X POST https://distributed-rate-limiter-production-82bf.up.railway.app/api/v1/keys \
  -H "Content-Type: application/json" \
  -d '{"algorithm":"sliding_window","limit":5,"windowSeconds":30}'

# Step 2 — Use the returned apiKey to check requests (run this 6 times)
curl -X POST https://distributed-rate-limiter-production-82bf.up.railway.app/api/v1/check \
  -H "Content-Type: application/json" \
  -d '{"apiKey":"YOUR_KEY","identifier":"test-user","cost":1}'
```

The first 5 requests return `"allowed": true`. The 6th returns `"allowed": false` with HTTP 429.

---

## The Problem This Solves

Most rate limiters only work on a **single server**. When you scale to multiple instances, each server keeps its own counter — so a user can bypass limits by hitting different servers.

**Without this service (broken):**
```
User → Server 1 → counter = 1  ✅
User → Server 2 → counter = 1  ✅  (should be 2!)
User → Server 3 → counter = 1  ✅  (should be 3!)
```
A user with a limit of 100 requests can actually send 300 — 100 to each server.

**With this service (correct):**
```
User → Server 1 → Redis counter = 1  ✅
User → Server 2 → Redis counter = 2  ✅
User → Server 3 → Redis counter = 3  ✅
```
All servers check the same Redis counter. Limits are enforced consistently.

---

## Why This Is Better Than Alternatives

| Feature | express-rate-limit | redis-rate-limit | This Service |
|---------|-------------------|-----------------|--------------|
| Works across multiple servers | ❌ No | ✅ Yes | ✅ Yes |
| Language support | Node.js only | Node.js only | Any (HTTP API) |
| Circuit breaker / fail-open | ❌ No | ❌ No | ✅ Yes |
| Sliding window algorithm | ❌ No | Partial | ✅ Atomic Lua |
| Token bucket algorithm | ❌ No | ❌ No | ✅ Yes |
| Built-in monitoring | ❌ No | ❌ No | ✅ Prometheus + Grafana |
| Load balanced | ❌ No | ❌ No | ✅ Nginx + 2 instances |
| Self hostable | ✅ Yes | ✅ Yes | ✅ Docker Compose |

**Key advantage:** Atomic Lua scripts guarantee no race conditions even under heavy concurrent load. `express-rate-limit` uses in-memory counters that reset on restart and don't share state across servers.

---

## Architecture

```
                    Internet
                       │
                  ┌────▼────┐
                  │  Nginx  │  ← Load balancer + first-layer limiting
                  └────┬────┘
           ┌───────────┴───────────┐
           │                       │
    ┌──────▼──────┐         ┌──────▼──────┐
    │  Service 1  │         │  Service 2  │  ← Spring Boot instances
    │  :8080      │         │  :8081      │
    └──────┬──────┘         └──────┬──────┘
           └───────────┬───────────┘
                  ┌────▼────┐
                  │  Redis  │  ← Shared state (atomic counters)
                  └─────────┘

    ┌─────────────┐   ┌─────────┐
    │ Prometheus  │──▶│ Grafana │  ← Monitoring
    └─────────────┘   └─────────┘
```

| Component | Technology | Purpose |
|-----------|-----------|---------|
| API Service | Spring Boot 3.5 | REST endpoints, algorithm execution |
| Shared State | Redis 7 | Atomic counters, API key storage |
| Load Balancer | Nginx | Traffic distribution, first-layer limiting |
| SDK | TypeScript / npm | Client library for Node.js apps |
| Monitoring | Prometheus + Grafana | Real-time metrics and dashboards |
| Orchestration | Docker Compose | Full stack in one command |
| Deployment | Railway | Live cloud hosting |

---

## Algorithms

### Sliding Window (Recommended)

Uses a Redis Sorted Set to store timestamped request entries. Each check atomically removes expired entries, counts remaining, and adds the new request if under the limit.

```lua
-- Atomic Lua script — runs on Redis in one indivisible operation
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

**Best for:** APIs where legitimate users occasionally need short bursts.

---

## How to Use It

### Option 1 — npm SDK (Node.js / TypeScript)

```bash
npm install @rajvardhan6403/rate-limiter-sdk
```

Basic usage:

```typescript
import { RateLimiter } from '@rajvardhan6403/rate-limiter-sdk';

const limiter = new RateLimiter({
  serviceUrl: 'https://distributed-rate-limiter-production-82bf.up.railway.app',
  apiKey: 'your-api-key',
  failOpen: true,
  timeoutMs: 3000,
});

const result = await limiter.check({ identifier: req.ip });

if (!result.allowed) {
  return res.status(429).json({ error: 'Rate limit exceeded' });
}
```

Express middleware — 3 lines of integration:

```typescript
import express from 'express';
import { RateLimiter } from '@rajvardhan6403/rate-limiter-sdk';

const app = express();
const limiter = new RateLimiter({
  serviceUrl: 'https://distributed-rate-limiter-production-82bf.up.railway.app',
  apiKey: 'your-api-key',
});

app.use(limiter.middleware());  // all routes are now rate limited
```

### Option 2 — Direct HTTP API (Any Language)

**Python:**
```python
import requests

response = requests.post(
    'https://distributed-rate-limiter-production-82bf.up.railway.app/api/v1/check',
    json={'apiKey': 'your-api-key', 'identifier': 'user-123', 'cost': 1}
)
data = response.json()
if not data['allowed']:
    print(f"Rate limited. Retry after {data['retryAfterSeconds']} seconds")
```

**Go:**
```go
resp, _ := http.Post(
    "https://distributed-rate-limiter-production-82bf.up.railway.app/api/v1/check",
    "application/json",
    strings.NewReader(`{"apiKey":"your-key","identifier":"user-123","cost":1}`),
)
```

---

## API Reference

### POST /api/v1/keys — Register API Key

```json
{ "algorithm": "sliding_window", "limit": 100, "windowSeconds": 60 }
```

### POST /api/v1/check — Check Rate Limit

```json
{ "apiKey": "abc123", "identifier": "user-456", "cost": 1 }
```

Response (200 — allowed):
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

Response (429 — rate limited):
```json
{ "allowed": false, "remaining": 0, "retryAfterSeconds": 30 }
```

### GET /api/v1/keys/{apiKey} — Get Policy
### DELETE /api/v1/keys/{apiKey} — Delete Key

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

| Service | URL |
|---------|-----|
| API via Nginx | http://localhost |
| Service Instance 1 | http://localhost:8080 |
| Service Instance 2 | http://localhost:8081 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |
| Redis | localhost:6379 |

---

## Monitoring

Grafana dashboard at http://localhost:3000 shows:

- **HTTP Requests per Second** — traffic across both instances
- **Average Response Time** — latency per endpoint in ms
- **JVM Heap Memory** — memory usage per instance in MB
- **Live JVM Threads** — active threads per instance

---

## Project Structure

```
distributed-rate-limiter/
├── service/                           # Spring Boot service
│   ├── src/main/java/com/ratelimiter/service/
│   │   ├── algorithm/                 # SlidingWindowLimiter, TokenBucketLimiter
│   │   ├── controller/                # REST endpoints
│   │   ├── dto/                       # Request/response objects
│   │   ├── exception/                 # Global error handling
│   │   ├── repository/                # Redis API key storage
│   │   └── service/                   # Strategy routing
│   ├── src/main/resources/scripts/    # sliding_window.lua, token_bucket.lua
│   └── Dockerfile
├── sdk/                               # TypeScript npm package
│   └── src/
│       ├── RateLimiter.ts
│       ├── core/                      # CircuitBreaker, LocalCache
│       ├── middleware/                # Express middleware
│       └── types/
├── nginx/nginx.conf
├── monitoring/
│   ├── prometheus.yml
│   └── grafana/provisioning/
├── Dockerfile                         # Root Dockerfile for Railway
├── docker-compose.yml
└── railway.toml
```

---

## Design Decisions

**Why Lua scripts?** Redis executes Lua atomically — no race conditions possible. Two simultaneous requests cannot both read the same counter value and both get approved.

**Why fail-open?** Rate limiting is best-effort protection. If the service goes down, keeping your app running is more important than strict enforcement. The circuit breaker opens after 5 failures and retries after 30 seconds.

**Why Sorted Sets?** Each request entry is stored with its timestamp as score. One `ZREMRANGEBYSCORE` command removes all expired entries — exact count, no approximation.

---

## Tech Stack

| Technology | Version | Usage |
|-----------|---------|-------|
| Java | 21 | Service runtime |
| Spring Boot | 3.5 | REST API framework |
| Spring Data Redis | 3.5 | Redis client (Lettuce) |
| Lua | — | Atomic Redis scripts |
| TypeScript | 5 | SDK language |
| tsup | 8 | SDK bundler (ESM + CJS) |
| Axios | 1.7 | HTTP client in SDK |
| Docker + Compose | — | Containerisation |
| Nginx | alpine | Load balancer |
| Prometheus | latest | Metrics collection |
| Grafana | latest | Metrics visualisation |
| Micrometer | 1.15 | Spring → Prometheus bridge |
| Railway | — | Cloud deployment |

---

## Key Points

**Distributed systems:** "Used Redis Lua scripts for atomic operations — ZREMRANGEBYSCORE and ZADD execute as one indivisible operation, preventing race conditions under concurrent load."

**Algorithm trade-offs:** "Sliding window stores one entry per request — more memory, more accurate. Token bucket stores just two values per key — more memory-efficient, better for bursty traffic."

**Resilience:** "Circuit breaker fails open during outages. Five failures open the circuit for 30 seconds, then one probe request tests recovery. Availability over strict enforcement."

**Developer experience:** "Three lines of Express middleware integration. The SDK handles caching, timeouts, and circuit breaking — the developer doesn't need to know Redis exists."

**Observability:** "Prometheus scrapes Actuator metrics every 15 seconds. Grafana shows request rate, response time, memory, and threads per instance in real time."

---

## About the Author

**Rajvardhan Deshmukh**

- GitHub: [@raj-deshmukh6403](https://github.com/raj-deshmukh6403)
- npm: [@rajvardhan6403](https://www.npmjs.com/~rajvardhan6403)

---

## Support This Project

If you found this useful:

- ⭐ **Star this repo** — helps others discover it
- 🐛 **Found a bug?** — open an [issue](https://github.com/raj-deshmukh6403/distributed-rate-limiter/issues)
- 💡 **Have an idea?** — open a [discussion](https://github.com/raj-deshmukh6403/distributed-rate-limiter/discussions)
- 📦 **Using the SDK?** — leave a review on [npm](https://www.npmjs.com/package/@rajvardhan6403/rate-limiter-sdk)

---

## License


MIT — free to use, modify, and distribute.
