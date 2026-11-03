export function newIdemKey() {
  return crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

async function request(path, options = {}) {
  const { headers, ...rest } = options;
  const res = await fetch(`/api${path}`, {
    ...rest,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(body.message || `Request failed (${res.status})`);
    err.code = body.error;
    err.minNextCents = body.minNextCents;
    throw err;
  }
  return body;
}

export const listAuctions = () => request('/auctions');

export const getAuction = (id) => request(`/auctions/${id}`);

export const placeBid = (id, bidder, amountCents, idempotencyKey) =>
  request(`/auctions/${id}/bids`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ bidder, amountCents }),
  });

export const pay = (id, payer, idempotencyKey) =>
  request(`/auctions/${id}/payments`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ payer }),
  });
