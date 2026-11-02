# Live Auction Platform

Real-time auction system: WebSocket-powered live bid updates, server-enforced
countdowns, push notifications, Redis-backed bidding with idempotency controls,
and an atomic simulated payment flow.

- **Backend:** Spring Boot 3 (Java 17), Spring WebSocket/STOMP, Spring Data Redis (Lua scripts)
- **Frontend:** React 18 + Vite, STOMP over SockJS
- **Infra:** Docker Compose (Redis + backend + frontend)

## Quick start

Prereqs: Docker with the Compose plugin.

```bash
docker compose up --build
```

Then open **http://localhost:3000**, enter a bidder name, and bid. Open the
site in a second browser/incognito window with a different bidder name to see
live outbid notifications between clients.

Seeded auctions run 3, 5, and 8 minutes; restart the stack (or flush Redis)
to reseed.

### Local development

```bash
# 1. Redis
docker run -d --name auction-redis -p 6379:6379 redis:7-alpine

# 2. Backend (Java 17 + Maven)
cd backend && mvn spring-boot:run

# 3. Frontend (Node 18+)
cd frontend && npm install && npm run dev
# → http://localhost:5173 (proxies /api and /ws to :8080)
```

Run backend tests:

```bash
cd backend && mvn test
```

## Architecture

```
React client ──HTTP──▶ Spring Boot API ──Lua──▶ Redis (state, idempotency, history)
     ▲   └────STOMP/SockJS───▶ WsRelay ◀──pub/sub──┘   (cross-instance fan-out)
     └──────────── /topic/auction/{id}/bids | /events ◀── AuctionCloser (1s tick)
```

- **Atomic bid placement** — `place_bid.lua` runs the whole write path in one
  Redis script: status check → minimum-increment check → state update → history
  append → idempotency record. No read-check-write races under concurrency;
  only increments over the current winning bid are ever recorded.
- **Idempotency** — clients send an `Idempotency-Key` header per operation.
  The key is consumed (SET NX EX) only inside the success branch, so retries
  after timeouts replay the original result instead of double-bidding, and
  deterministic rejections stay retryable.
- **Countdowns** — clients render `endsAt` locally (synced via `serverNow`
  clock-offset), while `AuctionCloser` flips expired auctions to `ENDED`
  exactly once via `HSETNX` and broadcasts the result.
- **Payment integrity** — `pay.lua` settles atomically: auction must be
  `ENDED`, payer must equal the recorded leader, amount is taken from
  server-side state (never the client), and `paid` can only flip once.
- **Scalable fan-out** — bid/end/payment events publish to local STOMP clients
  and to a Redis pub/sub channel tagged with an instance id, so stateless API
  replicas forward events to their own WebSocket connections without
  duplicates.

## HTTP API

| Method | Path | Headers | Body |
| --- | --- | --- | --- |
| GET | `/api/auctions` | — | — |
| GET | `/api/auctions/{id}` | — | — |
| POST | `/api/auctions/{id}/bids` | `Idempotency-Key` | `{ "bidder", "amountCents" }` |
| POST | `/api/auctions/{id}/payments` | `Idempotency-Key` | `{ "payer" }` |

Amounts are integer cents. Errors are JSON: `{ "error", "message", ... }`
(e.g. `BID_TOO_LOW` includes `minNextCents`).

## WebSocket topics (STOMP over SockJS at `/ws`)

- `/topic/auction/{id}/bids` — `Bid` on every accepted bid
- `/topic/auction/{id}/events` — `{ type: BID_ACCEPTED \| AUCTION_ENDED \| PAYMENT_COMPLETED, auction? \| bid? \| receipt? }`

## Redis keys

| Key | Type | Purpose |
| --- | --- | --- |
| `auction:{id}` | string (JSON) | catalog record |
| `auction:{id}:state` | hash | hot fields mutated by Lua |
| `auction:{id}:bids` | list | recent bids, newest first |
| `auction:{id}:payment` | string (JSON) | receipt, set once |
| `auction:idem:{key}` | string | idempotency slots |
| `auction:index:active` / `:ended` | set | scheduler work queue |

## Project layout

```
backend/   Spring Boot app (config, services, controllers, Lua scripts, tests)
frontend/  React app (STOMP client, auction list/detail, countdown, toasts)
docker-compose.yml
```
