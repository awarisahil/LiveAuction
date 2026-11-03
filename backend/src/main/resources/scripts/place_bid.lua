-- Atomically validates and places a bid.
--
-- KEYS[1] = auction:{id}:state   (hash: currentBidCents, minIncrementCents, status, endsAt,
--                                       leader (user id), leaderName, bidCount)
-- KEYS[2] = auction:idem:{key}   (idempotency slot for this bid attempt)
-- KEYS[3] = auction:{id}:bids    (list of recent bids, JSON encoded, newest first)
--
-- ARGV[1] = bidId
-- ARGV[2] = bidder user id
-- ARGV[3] = bidder display name
-- ARGV[4] = amountCents
-- ARGV[5] = now (epoch millis)
-- ARGV[6] = idempotency key TTL (seconds)
-- ARGV[7] = recent bids list max length
-- ARGV[8] = anti-snipe window (ms); a bid inside the window extends the deadline
--
-- Returns {status, payload}:
--   { 1, bidId }        bid accepted
--   { 0, bidId }        idempotent replay of an already-accepted bid
--   {-1, minNextCents } amount below the required minimum
--   {-2, status }       auction closed (ended/cancelled or past deadline)

local existing = redis.call('GET', KEYS[2])
if existing then
    return {0, existing}
end

local status = redis.call('HGET', KEYS[1], 'status') or 'ACTIVE'
if status ~= 'ACTIVE' then
    return {-2, status}
end

local now = tonumber(ARGV[5])
local endsAt = tonumber(redis.call('HGET', KEYS[1], 'endsAt'))
if endsAt and now > endsAt then
    return {-2, 'ENDED'}
end

local current = tonumber(redis.call('HGET', KEYS[1], 'currentBidCents')) or 0
local minInc = tonumber(redis.call('HGET', KEYS[1], 'minIncrementCents')) or 1
local amount = tonumber(ARGV[4])
local minNext = current + minInc

if amount < minNext then
    return {-1, tostring(minNext)}
end

redis.call('HSET', KEYS[1],
    'currentBidCents', amount,
    'leader', ARGV[2],
    'leaderName', ARGV[3])
redis.call('HINCRBY', KEYS[1], 'bidCount', 1)

-- Anti-sniping: a bid in the final window extends the deadline once per bid.
if endsAt and (endsAt - now) < tonumber(ARGV[8]) then
    redis.call('HSET', KEYS[1], 'endsAt', now + tonumber(ARGV[8]))
end

local bid = cjson.encode({
    id = ARGV[1],
    bidderId = ARGV[2],
    bidder = ARGV[3],
    amountCents = amount,
    at = now
})
redis.call('LPUSH', KEYS[3], bid)
redis.call('LTRIM', KEYS[3], 0, tonumber(ARGV[7]) - 1)
redis.call('INCR', 'platform:bids')

-- Consume the idempotency key only on success so failed attempts stay retryable.
redis.call('SET', KEYS[2], ARGV[1], 'EX', tonumber(ARGV[6]))

return {1, ARGV[1]}
