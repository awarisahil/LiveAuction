-- Claims an expired active auction and marks it ENDED exactly once.
--
-- KEYS[1] = auction:{id}:state
-- ARGV[1] = now (epoch millis, unused by the claim but kept for symmetry/logging)
--
-- Returns {status, payload}:
--   { 1, 'ENDED' }  this caller claimed the transition
--   { 0, status }   already claimed / not active

local status = redis.call('HGET', KEYS[1], 'status') or 'ACTIVE'
if status ~= 'ACTIVE' then
    return {0, status}
end

local claimed = redis.call('HSETNX', KEYS[1], 'status', 'ENDED')
if claimed == 1 then
    return {1, 'ENDED'}
end

return {0, redis.call('HGET', KEYS[1], 'status') or 'ACTIVE'}
