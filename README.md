# BoltBid — Live Auction Platform

A full-featured real-time auction platform: WebSocket-powered live bidding,
accounts with roles (buyer / seller / admin), seller listings, wallet payments
with automatic seller payouts, anti-sniping countdowns, and an admin console.

- **Backend:** Spring Boot 3 (Java 17), JWT auth, Spring WebSocket/STOMP, Spring Data Redis (Lua scripts)
- **Frontend:** React 18 + Vite + React Router, STOMP over SockJS
- **Infra:** Docker Compose (Redis + backend + frontend)

## Quick start

```bash
docker compose up --build
```

Open **http://localhost:3000**. Local dev instead:

```bash
docker run -d -p 6379:6379 redis:7-alpine
cd backend && mvn spring-boot:run      # http://localhost:8080
cd frontend && npm install && npm run dev   # http://localhost:5173
```

### Demo accounts (seeded)

| Role | Email | Password | Wallet |
| --- | --- | --- | --- |
| Buyer | buyer@demo.com | buyer123 | $50,000 |
| Seller | seller@demo.com | seller123 | — (earns from sales) |
| Admin | admin@demo.com | admin123 | — |

Reseed anytime: `redis-cli flushall` then restart the backend.

## Features

**Accounts & trust** — register as buyer or seller, BCrypt-hashed passwords,
JWT sessions. Banned accounts can't bid; sellers can't bid on their own
listings; admins can ban/unban, promote/demote roles, and cancel auctions.

**Selling** — sellers list items with category, starting price, minimum
increment, duration (1 min – 7 days), photo emoji or image URL. Live catalog
with search + category filters.

**Bidding** — every bid runs as one atomic Redis Lua script: status/deadline
check, minimum-increment check, state update, history append, idempotency
record. Idempotency keys make retries safe (replays return the original
result, never a duplicate bid). **Anti-sniping:** a bid in the final 30s
extends the deadline by 30s.

**Wallet payments** — winners settle from their wallet (instant demo
deposits). Settlement is a single atomic script: winner guard, single-payment
guard, balance check, buyer debit, seller credit (95%), platform fee (5%),
receipt, idempotency. Sellers are paid out automatically.

**Profiles** — avatar/bio, wallet, bid history, winnings (with pay status),
listings, payment receipts, watchlist with star toggles on every auction.

**Admin console** — platform stats (users, live/finished auctions, total
bids, GMV, revenue), user management, auction moderation.

**Realtime** — STOMP topics per auction push bids, extensions, ends,
cancellations and payments to all connected clients; a Redis pub/sub bridge
with instance-id dedup fans events out across stateless replicas.

## HTTP API overview

Auth: `POST /api/auth/register` · `POST /api/auth/login` (both → `{token, user}`)

Auctions: `GET /api/auctions?q=&category=` · `GET /api/auctions/{id}` ·
`POST /api/auctions` (SELLER) · `POST /api/auctions/{id}/bids` (auth,
`Idempotency-Key` header) · `POST /api/auctions/{id}/payments` (winner, auth,
idem key)

Me: `GET/PATCH /api/me` · `POST /api/me/wallet/deposit` ·
`GET /api/me/bids|winnings|listings|payments|watchlist` ·
`PUT/DELETE /api/me/watchlist/{id}`

Users: `GET /api/users/{id}` (public profile + stats)

Admin: `GET /api/admin/stats|users|auctions` · `PATCH /api/admin/users/{id}` ·
`POST /api/admin/auctions/{id}/cancel`

Errors are JSON: `{ "error", "message", ... }` (`BID_TOO_LOW` includes
`minNextCents`).

## Architecture

```
React SPA ──HTTP/JWT──▶ Spring Boot API ──Lua──▶ Redis (catalog, state, wallets, idempotency)
     ▲   └────STOMP/SockJS────▶ WsRelay ◀──pub/sub (instance-tagged)──┘
     └──────────────── /topic/auction/{id}/bids | /events ◀── AuctionCloser (1s tick)
```

Integrity invariants live in Lua and run atomically under concurrency:
bid placement (`place_bid.lua`), wallet settlement (`pay.lua`), and status
transitions (`claim_end.lua`, `set_status.lua`).

## Development status

- Backend: 12 unit tests green (`mvn test`) covering bid/payment outcomes,
  idempotent replay, and trust guards.
- Full lifecycle smoke-tested against a live stack (register → list → bid →
  outbid → auto-close → wallet payment → payouts → admin stats).

Not included (natural next steps): ratings & reviews, email verification,
image upload storage, real payment gateway (Stripe), saved searches,
notifications inbox, horizontal-scale load tests.
