# System Design — Caching

## Part 1 — Concepts (read this first)

### Why caching is needed
The core idea: some data is **expensive to produce but cheap to reuse** — a slow DB query, a heavy computation, a call to a downstream service — and is requested **far more often than it actually changes**. Serving repeat requests from a fast, small, in-memory (or otherwise faster) store instead of redoing the expensive work every time turns an `O(expensive)` operation into an `O(cheap)` lookup for every request after the first. This trades **staleness risk** (the cached copy can lag behind the real source) for **latency and load reduction** — a deliberate tradeoff, not a free win, which is why invalidation strategy (below) is the actual hard part of caching, not the lookup itself.

### Cache-aside (lazy loading) pattern
The most common pattern, and the default to reach for unless something specific argues otherwise:
```
Read path:
  1. App checks the cache for key.
  2. Cache HIT  -> return cached value. Done.
  3. Cache MISS -> app reads from the DB (source of truth),
                    writes that value into the cache,
                    then returns it.

Write path:
  1. App writes to the DB.
  2. App invalidates (deletes, or updates) the corresponding cache entry.
```
```
     ┌────────┐   1. get(key)   ┌───────┐
     │  App    │ ─────────────> │ Cache  │
     │         │ <───────────── │        │
     └────┬────┘   2a. HIT      └───────┘
          │
          │ 2b. MISS
          ▼
     ┌────────┐   3. read       ┌───────┐
     │  App    │ ─────────────> │  DB    │
     │         │ <───────────── │        │
     └────┬────┘                └───────┘
          │ 4. populate cache
          ▼
     ┌───────┐
     │ Cache  │
     └───────┘
```
**Why it's the default**: the cache only ever holds what's actually been requested (no wasted memory pre-loading things nobody asks for), and the application controls exactly what gets cached and for how long. Downside: the **first** request for any given key is always slow (a "cache miss" pays the full DB cost) — this is the well-known **cold cache** / **thundering herd** risk (many concurrent requests for the same freshly-missed key can all pile onto the DB simultaneously before the first one finishes populating the cache).

### Read-through vs write-through
Different division of responsibility for *who* talks to the cache:
- **Cache-aside** (above): the **application** owns the logic — checks cache, falls back to DB, populates cache. The cache itself is "dumb," just a key-value store.
- **Read-through**: the **cache library/layer** itself is responsible for the miss-then-populate logic — the app just asks the cache for the value, and the cache transparently loads it from the DB on a miss and returns it, without the app writing that fallback logic itself. Functionally similar outcome to cache-aside, different ownership of the miss-handling code.
- **Write-through**: every **write** goes to the cache first (or simultaneously), and the cache itself is responsible for persisting it to the DB, synchronously, before the write is considered complete. Guarantees the cache is **never stale** immediately after a write (unlike cache-aside's separate "write DB, then invalidate cache" two-step, where a crash or race between those two steps can leave them briefly inconsistent) — at the cost of every write now paying the cache-write latency in addition to the DB-write latency, on the critical path.

**Practical default**: cache-aside for reads (simplest, most control, no special cache infrastructure required beyond a plain key-value store) with an explicit invalidate-on-write for the write side — this is what most applications, including the mini project's plan, actually use; read-through/write-through are more relevant when using a caching layer/library specifically designed to provide that abstraction (some ORM-level or CDN-level caches work this way).

### Cache invalidation — the genuinely hard part
Famously one of the "two hard things in computer science" (cache invalidation and naming things) because there's no single correct answer — the strategy has to be chosen deliberately per use case:
- **TTL (time-to-live)** — every cache entry expires automatically after a fixed duration, regardless of whether the underlying data actually changed. Simple, self-healing (a stale entry can only live for at most the TTL), but means the cache **can** serve stale data for up to that window — acceptable when brief staleness is tolerable (e.g. a department list that rarely changes and being 60 seconds out of date is a non-issue).
- **Write-through invalidation (explicit)** — on every write to the DB, the application explicitly deletes (or updates) the corresponding cache key, so the next read is guaranteed a miss-and-refresh. Stronger consistency guarantee than TTL alone (no window where a *known* stale value is served), but requires the write path to remember to do this correctly for every code path that mutates that data — a missed invalidation site is a real, easy-to-introduce bug class.
- **Delete vs update on invalidation**: deleting the cache key on write (forcing the next read to be a cache-aside miss that repopulates from the DB) is generally safer than updating the cache value directly in the write path, because it avoids a second place that has to correctly reconstruct exactly what the cached representation should look like — one source of truth (the DB) for what gets cached, always via the same read-path logic.
- **Combining both** (common in practice): TTL as a safety net (bounds worst-case staleness even if an invalidation site is missed) plus explicit invalidation on write (keeps the common case fresh immediately) — belt-and-suspenders rather than picking one exclusively.

### When to use Redis
Redis is an in-memory key-value store — the most common concrete choice for implementing any of the above patterns, because:
- **Speed**: in-memory reads/writes, sub-millisecond, versus a DB round-trip that involves disk I/O, query planning, and often a network hop to a separate DB server anyway.
- **Shared across instances**: unlike an in-process/local cache (a plain `HashMap` in application memory), Redis is a separate service every application instance talks to — critical the moment there's more than one server instance (horizontal scaling), since a local-only cache would mean each instance has its own inconsistent view and cache warmth doesn't transfer between instances.
- **Built-in TTL support**: `EXPIRE key seconds` (or `SET key value EX seconds`) natively supports the TTL invalidation strategy above without the application having to track expiry itself.
- **Beyond plain KV**: data structures (lists, sets, sorted sets, hashes) useful for more than caching — rate limiting, session storage, leaderboards, pub/sub — relevant background for [Day 6's JWT doc](03-springboot-security-jwt.md#q-jwts-are-stateless-and-cant-be-looked-up-server-side--so-how-do-you-revoke-one-before-it-expires-eg-on-logout-or-a-compromised-account), which named Redis as the natural store for a token denylist with TTL matching token expiry.

**When *not* to reach for Redis**: a single-instance application with a small, cheap-to-recompute dataset may not need a caching layer at all — an in-process cache (or no cache) is simpler and has one less moving part/operational dependency to run and monitor. Caching is a deliberate answer to a measured latency/load problem, not a default addition to every project.

---

## Part 2 — Applying this to the Employee API

From [Day 5's mini-project plan](../day5/07-mini-project-plan.md#part-1--stack-and-the-one-blocking-version-note), Redis is Phase 6, specifically for **employee-by-id** and **department list** lookups:
- **Pattern**: cache-aside. On `GET /employees/{id}`, check Redis for key `employee:{id}` first; on a miss, read from Postgres, populate Redis, return. On `PUT/PATCH /employees/{id}` or delete, explicitly `DEL employee:{id}` (invalidate, don't try to update the cached JSON in place — simpler, one source of truth for what gets cached).
- **Why employee-by-id specifically**: single-entity lookups by primary key are the textbook cache-aside case — a stable key, a clear invalidation point (the one endpoint that writes that entity), and a read pattern (repeated lookups of the same popular employees, e.g. a manager's own reports viewed repeatedly) that benefits from avoiding a repeated Postgres round-trip.
- **Why department list**: rarely changes (departments aren't created/renamed often) relative to how often it's read (likely on every employee list/detail page for display) — a strong TTL candidate (e.g. 5-minute TTL) even without wiring up explicit invalidation on department writes, since staleness tolerance is high and write frequency is low.
- **Invalidation strategy to commit to, explicitly** (per Day 5's plan, this needs to be a deliberate choice, not a default): write-through invalidation (`DEL` on update) for employee-by-id, since employee data changing and a caller immediately seeing stale data (e.g. right after an admin corrects a salary) is a real, visible problem worth avoiding; TTL-only for department list, since staleness there is low-stakes and explicit invalidation on every department mutation is more code for little benefit given how rarely departments change.
- **What this answers in an interview**: "how do you handle a slow endpoint" → point at exactly this: measured (or anticipated) repeat-read pattern on employee-by-id, cache-aside with Redis, explicit invalidation on write — a concrete, defensible answer with a stated tradeoff, not just "we added Redis."
