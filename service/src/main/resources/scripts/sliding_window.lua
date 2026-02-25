-- sliding_window.lua
-- Atomically checks and records a request using a sliding window algorithm.
--
-- KEYS[1] = the Redis key for this identifier (e.g. "rl:user:123")
-- ARGV[1] = current timestamp in milliseconds
-- ARGV[2] = window size in milliseconds
-- ARGV[3] = max requests allowed in the window
-- ARGV[4] = unique request ID (to avoid duplicate entries)
--
-- Returns: { allowed (1/0), remaining }

local key        = KEYS[1]
local now        = tonumber(ARGV[1])
local windowMs   = tonumber(ARGV[2])
local limit      = tonumber(ARGV[3])
local requestId  = ARGV[4]

-- Step 1: Remove all entries outside the current window
-- Everything with a score (timestamp) older than now-windowMs is expired
redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMs)

-- Step 2: Count how many requests are in the current window
local count = redis.call('ZCARD', key)

-- Step 3: Make the decision
if count < limit then
    -- Allow: add this request as a new entry in the sorted set
    -- Score = timestamp, Value = unique requestId
    redis.call('ZADD', key, now, requestId)

    -- Set the key to expire automatically after the window
    -- so Redis doesn't hold onto keys for inactive users forever
    redis.call('EXPIRE', key, math.ceil(windowMs / 1000) + 1)

    return { 1, limit - count - 1 }
else
    -- Deny: do not add the request, return 0 remaining
    return { 0, 0 }
end