-- Atomic compare-and-set on the auction status.
--
-- KEYS[1] = auction:{id}:state
-- ARGV[1] = expected status
-- ARGV[2] = new status
--
-- Returns {status, payload}:
--   { 1, newStatus }  transition applied by this caller
--   { 0, status }     current (unexpected) status, no change

local status = redis.call('HGET', KEYS[1], 'status') or 'ACTIVE'
if status ~= ARGV[1] then
    return {0, status}
end

redis.call('HSET', KEYS[1], 'status', ARGV[2])
return {1, ARGV[2]}
