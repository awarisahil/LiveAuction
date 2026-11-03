const TOKEN_KEY = 'auction.token';
const USER_KEY = 'auction.user';

export const getToken = () => localStorage.getItem(TOKEN_KEY);

export function setSession({ token, user }) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export const getStoredUser = () => {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null');
  } catch {
    return null;
  }
};

export function newIdemKey() {
  return crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

async function request(path, options = {}) {
  const { headers, ...rest } = options;
  const token = getToken();
  const res = await fetch(`/api${path}`, {
    ...rest,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(body.message || `Request failed (${res.status})`);
    err.code = body.error;
    err.status = res.status;
    err.minNextCents = body.minNextCents;
    throw err;
  }
  return body;
}

export const api = {
  // auth
  login: (email, password) =>
    request('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  register: (payload) =>
    request('/auth/register', { method: 'POST', body: JSON.stringify(payload) }),

  // auctions
  auctions: (q = '', category = '') => {
    const params = new URLSearchParams();
    if (q) params.set('q', q);
    if (category) params.set('category', category);
    const suffix = params.toString() ? `?${params}` : '';
    return request(`/auctions${suffix}`);
  },
  auction: (id) => request(`/auctions/${id}`),
  createAuction: (payload) =>
    request('/auctions', { method: 'POST', body: JSON.stringify(payload) }),
  placeBid: (id, amountCents, idemKey) =>
    request(`/auctions/${id}/bids`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idemKey },
      body: JSON.stringify({ amountCents }),
    }),
  pay: (id, idemKey) =>
    request(`/auctions/${id}/payments`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idemKey },
      body: '{}',
    }),

  // me
  me: () => request('/me'),
  updateMe: (payload) => request('/me', { method: 'PATCH', body: JSON.stringify(payload) }),
  deposit: (amountCents) =>
    request('/me/wallet/deposit', { method: 'POST', body: JSON.stringify({ amountCents }) }),
  myBids: () => request('/me/bids'),
  winnings: () => request('/me/winnings'),
  listings: () => request('/me/listings'),
  payments: () => request('/me/payments'),
  watchlist: () => request('/me/watchlist'),
  watch: (id) => request(`/me/watchlist/${id}`, { method: 'PUT' }),
  unwatch: (id) => request(`/me/watchlist/${id}`, { method: 'DELETE' }),

  // public profiles
  user: (id) => request(`/users/${id}`),

  // admin
  adminStats: () => request('/admin/stats'),
  adminUsers: () => request('/admin/users'),
  adminUpdateUser: (id, payload) =>
    request(`/admin/users/${id}`, { method: 'PATCH', body: JSON.stringify(payload) }),
  adminAuctions: () => request('/admin/auctions'),
  adminCancel: (id) => request(`/admin/auctions/${id}/cancel`, { method: 'POST', body: '{}' }),
};
