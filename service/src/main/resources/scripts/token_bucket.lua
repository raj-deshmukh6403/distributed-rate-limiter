-- token_bucket.lua
-- Atomically checks and consumes a token from a user's bucket.
--
-- KEYS[1] = the Redis key for this identifier (e.g. "rl:tb:user:123")
-- ARGV[1] = current timestamp in milliseconds
-- ARGV[2] = bucket capacity (max tokens)
-- ARGV[3] = refill rate (tokens per second)
--
-- Returns: { allowed (1/0), remaining_tokens }

local key        = KEYS[1]
local now        = tonumber(ARGV[1])
local capacity   = tonumber(ARGV[2])
local refillRate = tonumber(ARGV[3])  -- tokens added per second

-- Get current bucket state from Redis hash
local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill')
local tokens     = tonumber(bucket[1])
local lastRefill = tonumber(bucket[2])

-- First request ever for this key — start with a full bucket
if tokens == nil then
    tokens     = capacity
    lastRefill = now
end

-- Calculate how many tokens to add based on time elapsed
local elapsed       = (now - lastRefill) / 1000  -- convert ms to seconds
local tokensToAdd   = elapsed * refillRate
tokens = math.min(capacity, tokens + tokensToAdd)

-- Make the decision
local allowed = 0
if tokens >= 1 then
    tokens  = tokens - 1
    allowed = 1
end

-- Save updated bucket state back to Redis
redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)
redis.call('EXPIRE', key, math.ceil(capacity / refillRate) + 60)

return { allowed, math.floor(tokens) }