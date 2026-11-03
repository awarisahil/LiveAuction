-- Atomically validates and places a bid.
--
-- KEYS[1] = auction:{id}:state   (hash: currentBidCents, minIncrementCents, status, leader, bidCount)
-- KEYS[2] = auction:idem:{key}   (idempotency slot for this bid attempt)
-- KEYS[3] = auction:{id}:bids    (list of recent bids, JSON encoded, newest first)
--
-- ARGV[1] = bidId
-- ARGV[2] = bidder
-- ARGV[3] = amountCents
-- ARGV[4] = now (epoch millis)
-- ARGV[5] = idempotency key TTL (seconds)
-- ARGV[6] = recent bids list max length
--
-- Returns {status, payload}:
--   { 1, bidId }        bid accepted
--   { 0, bidId }        idempotent replay of an already-accepted bid
--   {-1, minNextCents } amount below the required minimum
--   {-2, status }       auction not active

local existing = redis.call('GET', KEYS[2])
if existing then
    return {0, existing}
end

local status = redis.call('HGET', KEYS[1], 'status') or 'ACTIVE'
if status ~= 'ACTIVE' then
    return {-2, status}
end

-- Reject bids arriving after the deadline even if the scheduler has not
-- flipped the status yet (scheduler tick gap).
local endsAt = tonumber(redis.call('HGET', KEYS[1], 'endsAt'))
if endsAt and tonumber(ARGV[4]) > endsAt then
    return {-2, 'ENDED'}
end

local current = tonumber(redis.call('HGET', KEYS[1], 'currentBidCents')) or 0
local minInc = tonumber(redis.call('HGET', KEYS[1], 'minIncrementCents')) or 1
local amount = tonumber(ARGV[3])
local minNext = current + minInc

if amount < minNext then
    return {-1, tostring(minNext)}
end

redis.call('HSET', KEYS[1],
    'currentBidCents', amount,
    'leader', ARGV[2],
    'status', status)
redis.call('HINCRBY', KEYS[1], 'bidCount', 1)

local bid = cjson.encode({
    id = ARGV[1],
    bidder = ARGV[2],
    amountCents = amount,
    at = tonumber(ARGV[4])
})
redis.call('LPUSH', KEYS[3], bid)
redis.call('LTRIM', KEYS[3], 0, tonumber(ARGV[6]) - 1)

-- Consume the idempotency key only on success so failed attempts stay retryable.
redis.call('SET', KEYS[2], ARGV[1], 'EX', tonumber(ARGV[5]))

return {1, ARGV[1]}
