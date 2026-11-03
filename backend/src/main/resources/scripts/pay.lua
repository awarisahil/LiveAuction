-- Atomically settles payment for a finished auction from the winner's wallet.
-- All integrity checks run inside the script so the decision is race-free:
-- only the recorded winner can pay, exactly once, for exactly the winning
-- amount, and only with sufficient balance. Payout (winning amount minus the
-- platform fee) is credited to the seller in the same atomic step.
--
-- KEYS[1] = auction:{id}:state
-- KEYS[2] = auction:idem:{key}      (idempotency slot for this payment attempt)
-- KEYS[3] = auction:{id}:payment    (receipt, set once)
-- KEYS[4] = user:{payerId}          (hash: balanceCents)
-- KEYS[5] = user:{sellerId}         (hash: balanceCents)
--
-- ARGV[1] = receiptId
-- ARGV[2] = payer user id
-- ARGV[3] = now (epoch millis)
-- ARGV[4] = idempotency key TTL (seconds)
-- ARGV[5] = platform fee in basis points (e.g. 500 = 5%)
--
-- Returns {status, payload}:
--   { 1, receiptId }     payment settled
--   { 0, receiptId }     idempotent replay of an already-recorded payment
--   {-2, status }        auction not ended yet
--   {-3, leaderName }    payer is not the winning leader
--   {-5, 'ALREADY_PAID'} auction already settled
--   {-6, shortCents }    insufficient wallet balance

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
    return {-3, redis.call('HGET', KEYS[1], 'leaderName') or ''}
end

if redis.call('HGET', KEYS[1], 'paid') == '1' then
    return {-5, 'ALREADY_PAID'}
end

local amount = tonumber(redis.call('HGET', KEYS[1], 'currentBidCents')) or 0
local payerBalance = tonumber(redis.call('HGET', KEYS[4], 'balanceCents')) or 0
if payerBalance < amount then
    return {-6, tostring(amount - payerBalance)}
end

local sellerBalance = tonumber(redis.call('HGET', KEYS[5], 'balanceCents')) or 0
local fee = math.floor(amount * tonumber(ARGV[5]) / 10000)
local sellerProceeds = amount - fee

redis.call('HSET', KEYS[4], 'balanceCents', payerBalance - amount)
redis.call('HSET', KEYS[5], 'balanceCents', sellerBalance + sellerProceeds)
redis.call('INCRBY', 'platform:revenue', fee)
redis.call('INCRBY', 'platform:gmv', amount)
redis.call('HSET', KEYS[1], 'paid', '1')

local receipt = cjson.encode({
    id = ARGV[1],
    payer = ARGV[2],
    amountCents = amount,
    feeCents = fee,
    sellerCents = sellerProceeds,
    paidAt = tonumber(ARGV[3])
})
redis.call('SET', KEYS[3], receipt)
redis.call('SET', KEYS[2], ARGV[1], 'EX', tonumber(ARGV[4]))

return {1, ARGV[1]}
