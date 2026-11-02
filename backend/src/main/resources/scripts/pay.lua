-- Atomically settles payment for a finished auction.
-- All integrity checks run inside the script so the decision is race-free:
-- only the recorded winner can pay, exactly once, for exactly the winning amount.
--
-- KEYS[1] = auction:{id}:state
-- KEYS[2] = auction:idem:{key}   (idempotency slot for this payment attempt)
-- KEYS[3] = auction:{id}:payment (receipt, set once)
--
-- ARGV[1] = receiptId
-- ARGV[2] = payer
-- ARGV[3] = now (epoch millis)
-- ARGV[4] = idempotency key TTL (seconds)
--
-- Returns {status, payload}:
--   { 1, receiptId }    payment recorded
--   { 0, receiptId }    idempotent replay of an already-recorded payment
--   {-2, status }       auction not ended yet
--   {-3, leader }       payer is not the winning leader
--   {-5, 'ALREADY_PAID'} auction already settled (e.g. retry without idempotency key)

local existing = redis.call('GET', KEYS[2])
if existing then
    return {0, existing}
end

local status = redis.call('HGET', KEYS[1], 'status') or 'ACTIVE'
if status ~= 'ENDED' then
    return {-2, status}
end

local leader = redis.call('HGET', KEYS[1], 'leader') or ''
if leader ~= ARGV[2] then
    return {-3, leader}
end

if redis.call('HGET', KEYS[1], 'paid') == '1' then
    return {-5, 'ALREADY_PAID'}
end

local amount = tonumber(redis.call('HGET', KEYS[1], 'currentBidCents')) or 0
redis.call('HSET', KEYS[1], 'paid', '1')

local receipt = cjson.encode({
    id = ARGV[1],
    payer = ARGV[2],
    amountCents = amount,
    paidAt = tonumber(ARGV[3])
})
redis.call('SET', KEYS[3], receipt)
redis.call('SET', KEYS[2], ARGV[1], 'EX', tonumber(ARGV[4]))

return {1, ARGV[1]}
