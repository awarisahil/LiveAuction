-- Claims an expired active auction and marks it ENDED exactly once.
-- The check-and-set is race-free because a Lua script runs atomically;
-- concurrent schedulers can never both claim the same auction.
--
-- KEYS[1] = auction:{id}:state
-- ARGV[1] = now (epoch millis, for logging/symmetry)
--
-- Returns {status, payload}:
--   { 1, 'ENDED' }  this caller claimed the transition
--   { 0, status }   not active / already claimed

local status = redis.call('HGET', KEYS[1], 'status') or 'ACTIVE'
if status ~= 'ACTIVE' then
    return {0, status}
end

redis.call('HSET', KEYS[1], 'status', 'ENDED')
return {1, 'ENDED'}
